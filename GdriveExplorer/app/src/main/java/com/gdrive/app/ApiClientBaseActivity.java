package com.gdrive.app;

import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.common.api.Status;

import java.io.IOException;

/**
 * A base class to wrap communication with the Google Play Services.
 */
public abstract class ApiClientBaseActivity extends Activity
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = ApiClientBaseActivity.class.getSimpleName();

    // A magic number we will use to know that our sign-in error resolution activity has completed
    private static final int OUR_REQUEST_CODE = 49404;

    // A flag to stop multiple dialogues appearing for the user
    private boolean mAutoResolveOnFail;

    // A flag to track when a connection is already in progress
    public boolean mPlusClientIsConnecting = false;

    // This is the helper object that connects to Google Play Services.
    private GoogleApiClient mGoogleApiClient;

    // The saved result from {@link #onConnectionFailed(ConnectionResult)}.  If a connection
    // attempt has been made, this is non-null.
    // If this IS null, then the connect method is still running.
    private ConnectionResult mConnectionResult;

    // The GoogleApiClient access token.
    private String accessToken = null;

    /**
     * Called when the {@link GoogleApiClient} is successfully connected to Google Play Services.
     */
    protected abstract void onConnectedToGooglePlayServices();

    /**
     * Called when the {@link GoogleApiClient} is disconnected from Google Play Services.
     */
    protected abstract void onDisconnectedFromGooglePlayServices();

    /**
     * Called when the {@link GoogleApiClient} is blocking the UI.  If you have a progress bar widget,
     * this tells you when to show or hide it.
     */
    protected abstract void onApiClientBlockingUI(boolean show);

    /**
     * Called when there is a change in connection state.  If you have "Sign in"/ "Connect",
     * "Sign out"/ "Disconnect", or "Revoke access" buttons, this lets you know when their states
     * need to be updated.
     */
    protected abstract void updateConnectButtonState();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize the GoogleApiClient connection.
        // Scopes indicate the information about the user your application will be able to access.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Plus.API)
                .addScope(Plus.SCOPE_PLUS_LOGIN)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addScope(Drive.SCOPE_APPFOLDER) // required for App Folder sample
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    /**
     * Try to sign in the user.
     */
    public void signIn() {
        if (!mGoogleApiClient.isConnected()) {
            // Show the dialog as we are now signing in.
            setProgressBarVisible(true);
            // Make sure that we will start the resolution (e.g. fire the intent and pop up a
            // dialog for the user) for any errors that come in.
            mAutoResolveOnFail = true;
            // We should always have a connection result ready to resolve,
            // so we can start that process.
            if (mConnectionResult != null) {
                startResolution();
            } else {
                // If we don't have one though, we can start connect in
                // order to retrieve one.
                initiateApiClientConnect();
            }
        }

        updateConnectButtonState();
    }

    /**
     * Connect the {@link GoogleApiClient} only if a connection isn't already in progress.  This will
     * call back to {@link #onConnected(android.os.Bundle)} or
     * {@link #onConnectionFailed(com.google.android.gms.common.ConnectionResult)}.
     */
    private void initiateApiClientConnect() {
        if (!mGoogleApiClient.isConnected() && !mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.connect();
        }
    }

    /**
     * Disconnect the {@link GoogleApiClient} only if it is connected (otherwise, it can throw an error.)
     * This will call back to {@link #onConnectionSuspended(int cause)}.
     */
    private void initiateApiClientDisconnect() {
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    /**
     * Sign out the user (so they can switch to another account).
     */
    public void signOut() {

        // We only want to sign out if we're connected.
        if (mGoogleApiClient.isConnected()) {
            // Clear the default account in order to allow the user to potentially choose a
            // different account from the account chooser.

            Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);

            // Disconnect from Google Play Services, then reconnect in order to restart the
            // process from scratch.
            initiateApiClientDisconnect();

            Log.v(TAG, "Sign out successful!");
        }

        updateConnectButtonState();
    }

    /**
     * Revoke Google+ authorization completely.
     */
    public void revokeAccess() {

        if (mGoogleApiClient.isConnected()) {
            // Clear the default account as in the Sign Out.
            Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);

            // Revoke access to this entire application. This will call back to
            // onAccessRevoked when it is complete, as it needs to reach the Google
            // authentication servers to revoke all tokens.
            Plus.AccountApi.revokeAccessAndDisconnect(mGoogleApiClient).setResultCallback(
                    new ResultCallback<Status>() {
                        @Override
                        public void onResult(Status status) {
                        // mGoogleApiClient is now disconnected and access has been revoked.
                        // Trigger app logic to comply with the developer policies
                            if(mGoogleApiClient.isConnected()){
                                // BUG: mGoogleApiClient is not disconnected here.
                                Log.e(TAG, "revokeAccessAndDisconnect mGoogleApiClient connect status: "
                                        + mGoogleApiClient.isConnected());
                                mGoogleApiClient.disconnect();
                            }
                            updateConnectButtonState();
                            onDisconnectedFromGooglePlayServices();
                        }
                    }
            );
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        initiateApiClientConnect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        initiateApiClientDisconnect();
    }

    public boolean isApiClientConnecting() {
        return mPlusClientIsConnecting;
    }

    private void setProgressBarVisible(boolean flag) {
        mPlusClientIsConnecting = flag;
        onApiClientBlockingUI(flag);
    }

    /**
     * A helper method to flip the mResolveOnFail flag and start the resolution
     * of the ConnectionResult from the failed connect() call.
     */
    private void startResolution() {
        try {
            // Don't start another resolution now until we have a result from the activity we're
            // about to start.
            mAutoResolveOnFail = false;
            // If we can resolve the error, then call start resolution and pass it an integer tag
            // we can use to track.
            // This means that when we get the onActivityResult callback we'll know it's from
            // being started here.
            mConnectionResult.startResolutionForResult(this, OUR_REQUEST_CODE);
        } catch (IntentSender.SendIntentException e) {
            // Any problems, just try to connect() again so we get a new ConnectionResult.
            mConnectionResult = null;
            initiateApiClientConnect();
        }
    }

    /**
     * An earlier connection failed, and we're now receiving the result of the resolution attempt
     * by PlusClient.
     *
     * @see #onConnectionFailed(ConnectionResult)
     */
    @Override
    protected void onActivityResult(int requestCode, int responseCode, Intent intent) {
        updateConnectButtonState();
        if (requestCode == OUR_REQUEST_CODE && responseCode == RESULT_OK) {
            // If we have a successful result, we will want to be able to resolve any further
            // errors, so turn on resolution with our flag.
            mAutoResolveOnFail = true;
            // If we have a successful result, let's call connect() again. If there are any more
            // errors to resolve we'll get our onConnectionFailed, but if not,
            // we'll get onConnected.
            initiateApiClientConnect();
        } else if (requestCode == OUR_REQUEST_CODE && responseCode != RESULT_OK) {
            // If we've got an error we can't resolve, we're no longer in the midst of signing
            // in, so we can stop the progress spinner.
            setProgressBarVisible(false);
        }
    }

    /**
     * Successfully connected (called by GoogleApiClient) to Google Play Services
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        updateConnectButtonState();
        setProgressBarVisible(false);
        onConnectedToGooglePlayServices();
    }

    /**
     * Called when the GoogleApiClient is temporarily in a disconnected state
     * from Google Play Services.
     */
    @Override
    public void onConnectionSuspended(int cause) {
        updateConnectButtonState();
        onDisconnectedFromGooglePlayServices();
    }

    /**
     * Connection failed for some reason (called by GoogleApiClient)
     * Try and resolve the result.  Failure here is usually not an indication of a serious error,
     * just that the user's input is needed.
     *
     * @see #onActivityResult(int, int, Intent)
     */
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        updateConnectButtonState();

        // Most of the time, the connection will fail with a user resolvable result. We can store
        // that in our mConnectionResult property ready to be used when the user clicks the
        // sign-in button.
        if (result.hasResolution()) {
            mConnectionResult = result;
            if (mAutoResolveOnFail) {
                // This is a local helper function that starts the resolution of the problem,
                // which may be showing the user an account chooser or similar.
                startResolution();
            }
        }
    }

    /**
     * Getter for {@code GoogleApiClient}.
     * @return An instance of {@link GoogleApiClient}.
     */
    public GoogleApiClient getApiClient() {
        return mGoogleApiClient;
    }

    /**
     * Getter for {@link GoogleApiClient} access token.
     * @return The accessToken string.
     */
    // http://stackoverflow.com/questions/17547019/calling-this-from-your-main-thread-can-lead-to-deadlock-and-or-anrs-while-getti
    private String getAccessToken() {
        if(accessToken != null){
            return accessToken;
        }

        try {
            accessToken = GoogleAuthUtil.getToken(this,
                    Plus.AccountApi.getAccountName(mGoogleApiClient),
                    "oauth2:SCOPE_PLUS_LOGIN SCOPE_FILE SCOPE_APPFOLDER");
        } catch (IOException transientEx) {
            // network or server error, the call is expected to succeed if you try again later.
            // Don't attempt to call again immediately - the request is likely to
            // fail, you'll hit quotas or back-off.
            accessToken = null;
        } catch (UserRecoverableAuthException e) {
            // Recover
            accessToken = null;
        } catch (GoogleAuthException authEx) {
            // Failure. The call is not expected to ever succeed so it should not be
            // retried.
            accessToken = null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return accessToken;
    }

    /**
     * Shows a toast message.
     * @param message The message string.
     */
    public void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}

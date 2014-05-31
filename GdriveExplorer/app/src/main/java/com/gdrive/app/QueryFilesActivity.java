package com.gdrive.app;

import android.os.Bundle;
import android.util.Log;
import android.widget.ListView;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import java.util.logging.Filter;


public class QueryFilesActivity extends ApiClientBaseActivity {

    private ListView mResultsListView;
    private ResultsAdapter mResultsAdapter;

    private static final String TAG = QueryFilesActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_query_files);
        mResultsListView = (ListView) findViewById(R.id.listViewResults);
        mResultsAdapter = new ResultsAdapter(this);
        mResultsListView.setAdapter(mResultsAdapter);
    }

    @Override
    protected void onConnectedToGooglePlayServices() {
        Log.i(TAG, "onConnectedToGooglePlayServices");

        Query query = new Query.Builder()
                .addFilter(Filters.or(Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.file"),
                        Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"),
                        Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.document")))
                .build();

        Drive.DriveApi.query(getApiClient(), query)
                .setResultCallback(metadataCallback);
    }

    @Override
    protected void onDisconnectedFromGooglePlayServices() {
        mResultsAdapter.clear();
    }

    @Override
    protected void onApiClientBlockingUI(boolean show) {

    }

    @Override
    protected void updateConnectButtonState() {

    }

    /**
     * Clears the result buffer to avoid memory leaks as soon
     * as the activity is no longer visible by the user.
     */
    @Override
    protected void onStop() {
        super.onStop();
        mResultsAdapter.clear();
    }

    final private ResultCallback<DriveApi.MetadataBufferResult> metadataCallback = new
            ResultCallback<DriveApi.MetadataBufferResult>() {
                @Override
                public void onResult(DriveApi.MetadataBufferResult result) {
                    if (!result.getStatus().isSuccess()) {
                        showMessage("Problem while retrieving results");
                        return;
                    }
                    Log.i(TAG, "metadataCallback getMetadataBuffer count: " + result.getMetadataBuffer().getCount());
                    mResultsAdapter.clear();
                    mResultsAdapter.append(result.getMetadataBuffer());
                }
            };
}

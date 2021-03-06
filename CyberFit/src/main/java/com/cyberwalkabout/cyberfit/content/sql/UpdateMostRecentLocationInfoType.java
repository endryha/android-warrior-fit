package com.cyberwalkabout.cyberfit.content.sql;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import com.cyberwalkabout.cyberfit.content.UriHelper;
import com.cyberwalkabout.cyberfit.db.sqlite.schema.table.LocationInfoTable;
import com.cyberwalkabout.cyberfit.model.v2.LocationInfo;
import com.cyberwalkabout.cyberfit.util.Const;

/**
 * @author Andrii Kovalov
 */
public class UpdateMostRecentLocationInfoType extends Update {
    private static final String TAG = UpdateMostRecentLocationInfoType.class.getSimpleName();

    public UpdateMostRecentLocationInfoType(Context context, SQLiteDatabase db, Uri uri) {
        super(context, db, uri, null, null, null);
    }

    @Override
    protected int update() {
        String id = uri.getLastPathSegment();
        try {
            LocationInfo.LocationType exerciseState = LocationInfo.LocationType.valueOf(uri.getQueryParameter(Const.STATE));

            ContentValues values = new ContentValues();
            values.put(LocationInfoTable.COLUMN_TYPE, exerciseState.name());
            values.put(LocationInfoTable.COLUMN_TIMESTAMP, System.currentTimeMillis());

            String selectLatestLocationInfo =
                    "select " + LocationInfoTable.COLUMN_ID + " from " + LocationInfoTable.TABLE_NAME + " where " + LocationInfoTable.COLUMN_EXERCISE_SESSION_ID + " = ? order by " + LocationInfoTable.COLUMN_TIMESTAMP + " desc limit 1";

            return db.update(LocationInfoTable.TABLE_NAME, values, LocationInfoTable.COLUMN_ID + " in (" + selectLatestLocationInfo + ")", new String[]{id});
        } catch (Exception e) {
            Log.e(TAG, "couldn't update exercise state", e);
        }
        return 0;
    }

    @Override
    protected void notifyChange() {
        context.getContentResolver().notifyChange(UriHelper.getInstance().allLocationInfo(), null);
    }
}

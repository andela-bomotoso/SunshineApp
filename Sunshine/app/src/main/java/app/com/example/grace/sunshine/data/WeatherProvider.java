package app.com.example.grace.sunshine.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;


public class WeatherProvider extends ContentProvider {

    private static final UriMatcher sUriMacther = buildUriMatcher();
    private WeatherDbHelper weatherDbHelper;

    static final int WEATHER = 100;
    static final int WEATHER_WITH_LOCATION = 101;
    static final int WEATHER_WITH_LOCATION_AND_DATE = 102;
    static final int LOCATION = 300;

    private static final SQLiteQueryBuilder weatherByLocationSettingQueryBuilder ;

    static {
        weatherByLocationSettingQueryBuilder = new SQLiteQueryBuilder();
        weatherByLocationSettingQueryBuilder.setTables(
                WeatherContract.WeatherEntry.TABLE_NAME + " INNER JOIN " +
                        WeatherContract.LocationEntry.TABLE_NAME +
                        " ON" + WeatherContract.WeatherEntry.TABLE_NAME + "." +
                        WeatherContract.WeatherEntry.COLUMN_LOC_KEY + " =" +
                        WeatherContract.LocationEntry.TABLE_NAME + "." +
                        WeatherContract.LocationEntry._ID);
    }

    private static final String locationSettingSelection = WeatherContract.LocationEntry.TABLE_NAME + "."+ WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ?";
    private static final String locationSettingWithStartDateSelection = WeatherContract.LocationEntry.TABLE_NAME + "." + WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ? AND " +
            WeatherContract.WeatherEntry.COLUMN_DATE + " >= ? ";
    private static final String locationSettingAndDaySelection =
            WeatherContract.LocationEntry.TABLE_NAME + " ." + WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " =? AND " + WeatherContract.WeatherEntry.COLUMN_DATE + " = ?" ;

    private void normalizeDate(ContentValues values) {
        if(values.containsKey(WeatherContract.WeatherEntry.COLUMN_DATE)) {
            long dateValue = values.getAsLong(WeatherContract.WeatherEntry.COLUMN_DATE);
            values.put(WeatherContract.WeatherEntry.COLUMN_DATE,WeatherContract.normalizeDate(dateValue));
        }
    }
    private Cursor getWeatherByLocationSetting(Uri uri, String[] projection, String sortOrder) {
        String locationSetting = WeatherContract.WeatherEntry.getLocationSettingFromUri(uri);
        long startDate = WeatherContract.WeatherEntry.getStartDateFromUri(uri);
        String[] selectionArgs;
        String selection;

        if(startDate == 0) {
            selection = locationSettingSelection;
            selectionArgs = new String[]{locationSetting};
        } else {
            selectionArgs = new String[]{locationSetting, Long.toString(startDate)};
            selection = locationSettingWithStartDateSelection;
        }
            return weatherByLocationSettingQueryBuilder.query(weatherDbHelper.getReadableDatabase(),projection,selection,selectionArgs,null,null,sortOrder);
    }

    private Cursor getWeatherByLocationSettingAndDate(Uri uri, String[] projection,String sortOrder) {
        String locationSetting = WeatherContract.WeatherEntry.getLocationSettingFromUri(uri);
        Long date = WeatherContract.WeatherEntry.getDateFromUri(uri);
        return weatherByLocationSettingQueryBuilder.query(weatherDbHelper.getReadableDatabase(),projection,locationSettingAndDaySelection,new String[]{locationSetting,Long.toString(date)},null,null,sortOrder);
    }

    static UriMatcher buildUriMatcher() {

        final UriMatcher matcher =  new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = WeatherContract.CONTENT_AUTHORITY;
        matcher.addURI(authority,WeatherContract.PATH_WEATHER,WEATHER);
        matcher.addURI(authority,WeatherContract.PATH_WEATHER + "/*", WEATHER_WITH_LOCATION );
        matcher.addURI(authority,WeatherContract.PATH_WEATHER + "/*/#",WEATHER_WITH_LOCATION_AND_DATE);
        matcher.addURI(authority,WeatherContract.PATH_LOCATION,LOCATION);
        return matcher;
    }

    @Override
    public String getType(Uri uri) {
        final int match = sUriMacther.match(uri);
        switch (match) {
            case WEATHER_WITH_LOCATION_AND_DATE:
                return WeatherContract.WeatherEntry.CONTENT_ITEM_TYPE;
            case WEATHER_WITH_LOCATION:
                return WeatherContract.WeatherEntry.CONTENT_TYPE;
            case WEATHER:
                return WeatherContract.WeatherEntry.CONTENT_TYPE;
            case LOCATION:
                return WeatherContract.WeatherEntry.CONTENT_TYPE;
            default:
                throw new UnsupportedOperationException("Unknown uri: "+uri);
        }

    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

        final SQLiteDatabase sqLiteDatabase = weatherDbHelper.getWritableDatabase();
        final int match = sUriMacther.match(uri);
        int rowsUpadated;

        switch (match) {
            case WEATHER:
                normalizeDate(values);
                rowsUpadated = sqLiteDatabase.update(WeatherContract.WeatherEntry.TABLE_NAME, values, selection, selectionArgs);
                break;
            case LOCATION:
                rowsUpadated = sqLiteDatabase.update(WeatherContract.LocationEntry.TABLE_NAME, values, selection, selectionArgs);
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        if(rowsUpadated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return rowsUpadated;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
       final SQLiteDatabase sqLiteDatabase = weatherDbHelper.getWritableDatabase();
       final int match = sUriMacther.match(uri);
        Uri returnUri;
        switch (match) {
            case WEATHER: {
                normalizeDate(values);
                long _id = sqLiteDatabase.insert(WeatherContract.WeatherEntry.TABLE_NAME,null,values);
                if(_id > 0) {
                    returnUri = WeatherContract.WeatherEntry.buildWeatherUri(_id);
                }
                else {
                    throw new android.database.SQLException("Failed to insert row into "+uri);
                }
                break;
            }

            case LOCATION: {
                long id = sqLiteDatabase.insert(WeatherContract.WeatherEntry.TABLE_NAME,null,values);
                if(id > 0) {
                    returnUri = WeatherContract.LocationEntry.buildLocationUri(id);
                }
                else {
                    throw new android.database.SQLException("Failed to insert row into "+uri);
                }
                break;
            }
            default:
                throw  new UnsupportedOperationException("Unknown uri: "+uri);
        }
        getContext().getContentResolver().notifyChange(uri,null);
        return returnUri;
    }

    @Override
    public boolean onCreate() {
        weatherDbHelper = new WeatherDbHelper(getContext());
        return true;
    }

    @Override
    public int delete(Uri uri, String selection,String[] selectionArgs) {
        final SQLiteDatabase sqLiteDatabase = weatherDbHelper.getWritableDatabase();
        final int match = sUriMacther.match(uri);
        int rowsDeleted;
        if (null == selection) selection = "1";
        switch (match) {
            case WEATHER:
                rowsDeleted = sqLiteDatabase.delete(WeatherContract.WeatherEntry.TABLE_NAME, selection, selectionArgs);
                break;
            case LOCATION:
                rowsDeleted = sqLiteDatabase.delete(WeatherContract.LocationEntry.TABLE_NAME, selection, selectionArgs);
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        if(rowsDeleted != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return rowsDeleted;
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        final SQLiteDatabase sqLiteDatabase = weatherDbHelper.getWritableDatabase();
        final int match = sUriMacther.match(uri);
        switch (match) {
            case WEATHER:
                sqLiteDatabase.beginTransaction();
                int returnCount = 0;
                try{
                    for(ContentValues value: values) {
                        long id = sqLiteDatabase.insert(WeatherContract.WeatherEntry.TABLE_NAME,null,value);
                        if(id != -1) {
                            returnCount++;
                        }
                    }
                    sqLiteDatabase.setTransactionSuccessful();
                }
                finally {
                    sqLiteDatabase.endTransaction();
                }
                getContext().getContentResolver().notifyChange(uri,null);
                return returnCount;
            default:
                return super.bulkInsert(uri,values);
        }
    }



    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,String sortOrder) {
       Cursor cursor;
        switch (sUriMacther.match(uri)) {
            case WEATHER_WITH_LOCATION_AND_DATE:
            {
                cursor = getWeatherByLocationSetting(uri,projection,sortOrder);
                break;
            }
            case WEATHER_WITH_LOCATION: {
                cursor = getWeatherByLocationSetting(uri,projection,sortOrder);
            }
            case WEATHER: {
                cursor = weatherDbHelper.getReadableDatabase().query(WeatherContract.WeatherEntry.TABLE_NAME,
                        projection,selection,selectionArgs,null,null,sortOrder);
                break;
            }
            case LOCATION: {
                cursor = weatherDbHelper.getReadableDatabase().query(WeatherContract.LocationEntry.TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            }

                default:
                    throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
             cursor.setNotificationUri(getContext().getContentResolver(),uri);
                return cursor;
            }

}

package app.com.example.grace.sunshine;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    List<String>weeklyForecast;
    ArrayAdapter<String> forecastAdapter;
    String[] forecastResult;
    private static final String EXTRA_TEXT = "extra_text";



    public ForecastFragment() {

    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
        //loadForeCast();
    }

    @Override
    public void onStart() {
        super.onStart();
        loadForeCast();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_map_display) {
           //loadForeCast();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    private void loadForeCast() {
        FetchWeatherTask weatherTask = new FetchWeatherTask();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = sharedPreferences.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));
        //String temperature = sharedPreferences.getString("temperature",getString(R.string.pref_temperature_metric));
        weatherTask.execute(location);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        setHasOptionsMenu(true);
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        final ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);

        weeklyForecast  = new ArrayList<>(Arrays.asList("Today - Sunny - 88/63", "Tomorrow - Foggy - 70/46",
                "Thursday - Cloudy 72/63", "Fri - Rainy - 64/51",
                "Sat - Foggy -70/46", "Sun - Sunny - 76/68", "Mon - Foggy - 56/40"));


        forecastAdapter = new ArrayAdapter<String>(getActivity(),R.layout.list_item_forecast, weeklyForecast);
        listView.setAdapter(forecastAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String forecast = forecastAdapter.getItem(position);
                Intent intent =  new Intent(getActivity(),DetailedActivity.class).putExtra(Intent.EXTRA_TEXT, forecast);
                //Toast.makeText(getActivity(),forecast,Toast.LENGTH_SHORT).show();
                startActivity(intent);
            }
        });

        return rootView;
    }


    public class FetchWeatherTask extends AsyncTask<String,Void,String[]>
    {
        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();
        private String getReadableDateString(long time) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE MMM dd");
            return simpleDateFormat.format(time);
        }

        private String formatHighLow(double high, double low) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType = sharedPreferences.getString(getString(R.string.pref_temperature_key),getString(R.string.pref_temperature_metric));
            if (unitType.equals(getString(R.string.pref_temperature_imperial))){
                high = (high * 1.8) + 32;
                low = (low * 1.8) + 32;
            }
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);
            String highLowStr = roundedHigh + "/" +roundedLow;
            return highLowStr;
        }

        private String[] getWeatherDataFromJSON(String forecastJsonStr, int numDays) throws JSONException{

            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWN_MAX = "max";
            final String OWN_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr.trim());
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            Time dayTime = new Time();
            dayTime.setToNow();
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);
            dayTime = new Time();

            String[] resultStrs =  new String[numDays];

            for(int i = 0; i<weatherArray.length();i++) {

                String day;
                String description;
                String highAndLow;

                JSONObject dayForecast =  weatherArray.getJSONObject(i);

                long dateTime;
                dateTime = dayTime.setJulianDay(julianStartDay + i);

                day = getReadableDateString(dateTime);
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);

                double high =  temperatureObject.getDouble(OWN_MAX);
                double low = temperatureObject.getDouble(OWN_MIN);
                highAndLow = formatHighLow(high,low);
                resultStrs[i] = day + " - "+description+" - "+ highAndLow;
            }

            return resultStrs;
        }

        @Override
        protected String[] doInBackground(String... params) {

            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;
            String forecastJsonStr = null;


            String format = "json";
            String units = "metric";
            int numDays = 7;
            try{
               final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";
                final String API_PARAM = "APPID";

                Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon().appendQueryParameter(
                        QUERY_PARAM,params[0]).appendQueryParameter(FORMAT_PARAM,format).
                        appendQueryParameter(UNITS_PARAM,units).appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .appendQueryParameter(API_PARAM,BuildConfig.OPEN_WEATHER_MAP_API_KEY).build();

                URL url = new URL(builtUri.toString());
                Log.v(LOG_TAG,"Built URI"+builtUri);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();

                if(inputStream == null) {
                    return null;
                }

                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;

                while((line = reader.readLine()) != null) {
                    buffer.append(line+"\n");
                }
                if(buffer.length() == 0) {
                    return null;
                }

                forecastJsonStr = buffer.toString();

                System.out.println(forecastJsonStr);
            }
            catch (IOException ioException) {
                return null;
            }
            finally {
                if(urlConnection == null) {
                    urlConnection.disconnect();
                }

                if(reader != null) {
                    try {
                        reader.close();
                    }
                    catch (final IOException ioException) {
                        Log.e("Placeholder Fragment", "Error", ioException);

                    }

                }
            }
            try {
                return getWeatherDataFromJSON(forecastJsonStr, numDays);

            }

            catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String[]result) {
            if(result != null) {
                forecastAdapter.clear();
                for(String dailyForecast:result) {
                    forecastAdapter.add(dailyForecast);
                }
            }
        }

    }
}

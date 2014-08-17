package com.example.android.sunshine;

import android.app.Fragment;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

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
import java.util.Date;
import java.util.List;

/**
 * Created by MittalPers on 08-08-2014.
 */
//public class ForecastFragment {
//}
/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    public ForecastFragment() {
    }

    private ArrayAdapter<String> forecastAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);    //For the fragment to handle menu events
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        //Handle action bar item clicks here. The Action bar will automatically handle clicks on the Home/Up button,
        //so long as a parent activity is specified in AndroidManifest.xml.
        int id = item.getItemId();
        if(id==R.id.action_refresh)
        {
            FetchWeatherTask weatherTask = new FetchWeatherTask();
            weatherTask.execute("94043");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //Populating the list with some dummy data

        String[] forecastArray = {
                "Today - Sunny - 88/63",
                "Tomorrow - Foggy - 70/40",
                "Weds - Cloudy - 72/63",
                "Thurs - Asteroids - 75/65",
                "Fri - Heavy Rain - 65/56",
                "Sat - Thunderstorm, Sing the Thunder Song! - 60/51",
                "Sun - Sunny - 80/68"
        };

        //List in Java for the Forecast
        List<String> weekForecast = new ArrayList<String>(Arrays.asList(forecastArray));

        //ArrayAdapter<String> forecastAdapter;
        //ArrayAdapter to take data from a source
        forecastAdapter = new ArrayAdapter<String>(
                getActivity(),                              //Current context (this fragment's parent activity)
                R.layout.list_item_forecast,                //ID of list item layout
                R.id.list_item_forecast_textview,           //ID of listview to populate
                weekForecast);                              //Forecast data

        //Get a reference to the ListView and attach this adapter to the forecast adapter

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        //Rootview is inflated at the beginning of the onCreate function
        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(forecastAdapter);

        return rootView;
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]>
    {
        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        /*The date time conversion code is going to be moved outside asynctask later,
     * so for convenience, break it out into its second method now.
    **/
        private String getReadableDateString(long time)
        //Because the API returns a UNIX timestamp (measured in sec),
        // it must be converted to ms in order to be converted to a valid date
        {
            Date date = new Date(time*1000);
            SimpleDateFormat format = new SimpleDateFormat("E, MMM d");
            return format.format(date).toString();
        }

        //Prepare for weather high/lows for presentation
        private String formatHighLows(double high, double low)
        //For presentation, assume the user doesn't care about tenths of a degree
        {
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         * Fortunately, parsing is easy: constructor takes the JSON string and converts it into an
         * Object hierarchy.
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException
        {
            //Names of JSON Objects to be extracted
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temperature";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DATETIME = "dt";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            String[] resultStrs = new String[numDays];
            for(int i = 0;  i < weatherArray.length(); i++)
            {
                //For now, Use the format "Day, Description high/low"
                String day;
                String description;
                String highAndLow;

                //Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                //Date/time is returned as a long. Need to convert that into something human readable
                long dateTime = dayForecast.getLong(OWM_DATETIME);
                day = getReadableDateString(dateTime);

                //Description in a child array called "Weather", which is 1 element long
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                //Temperatures are in a child object called "temp".
                // Variables related to temperature shouldn't be named temp.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high, low);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }

            for(String s : resultStrs)
            {
                Log.v(LOG_TAG, "Forecast Entry:" + s);
            }

            return resultStrs;
        }

        @Override
        protected String[] doInBackground(String... params)
        {
            //No zip code means nothing to look up. Verify size of params.
            if(params.length == 0)
            {
                return null;
            }

            //These 2 need to be declared outside the try/catch so that they can be closed in the finally block
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            //Will contain the raw JSON response as a string
            String forecastJsonStr = null;

            String format = "json";
            String units = "metric";
            int numDays = 7;

            try{
                //Construct the URL for the OpenWeatherMap query
                //Possible parameters are available at OWM's forecast API page, at http://openweathermap.org/API#forecast
                final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";

                Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, params[0])
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .build();

                URL url = new URL(builtUri.toString());

                //Log.v(LOG_TAG, "Built URI" + builtUri.toString());

                //URL url = new URL ("http://api.openweathermap.org/data/2.5/forecast/daily?q=94305&mode=json&units=metric&cnt=7");

                //Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                //Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if(inputStream==null)
                {
                    //Nothing to do
                    return null;
                }
                reader=new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while((line=reader.readLine())!=null)
                {
                    //Since it's JSON, adding a new line isn't necessary (it won't affect parsing)
                    //But it does make debugging a lot easier if the completed buffer is printed for debugging.
                    buffer.append(line + "\n");
                }

                if(buffer.length()==0)
                {
                    //Stream was empty. No point in parsing.
                    //forecastJsonStr=null;
                    return null;
                }
                forecastJsonStr=buffer.toString();

                Log.v(LOG_TAG, "Forecast JSON String: " + forecastJsonStr);
            }

            catch(IOException e){
                Log.e(LOG_TAG, "Error", e);
                //If the code didn't successfully get the weather data, there's no point in attempting to parse it.
                return null;
            }

            finally
            {
                if(urlConnection!=null)
                {
                    urlConnection.disconnect();
                }

                if(reader!=null)
                {
                    try
                    {
                        reader.close();
                    }
                    catch(final IOException e)
                    {
                        Log.e(LOG_TAG, "Error Closing Stream", e);
                    }
                }
            }

            try
            {
                return getWeatherDataFromJson(forecastJsonStr, numDays);
            }
            catch(JSONException e)
            {
                //Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }

            return null;
        }
        //New data from Server
        @Override
        protected void onPostExecute(String[] result)
        {
            if(result!=null)
            {
                forecastAdapter.clear();
                for(String dayForecastStr : result)
                {
                    forecastAdapter.add(dayForecastStr);
                }
            }
        }
    }

}
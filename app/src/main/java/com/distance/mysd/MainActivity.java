package com.distance.mysd;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener, LocationListener {
    String TAG = "MainActivity: ";
    static FirebaseFirestore db = FirebaseFirestore.getInstance();
    static CollectionReference zoneStatusCollection = db.collection("city");
    Vibrator vibrator;
    static boolean vibrating = false;

    Switch onOffSwitch;
    Switch distancing;


    private RecyclerView fireStoreList;
    private FirestoreRecyclerAdapter adapter;

    protected LocationManager locationManager;
    protected LocationListener locationListener;
    TextView cityZoneKey;
    TextView cityZone;
    TextView txtLat;
    TextView resultTextView;

    Map<String, CityModel> cityModelMap = new HashMap<>();

    BluetoothLE ble;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Bluetooth Low Energy object
        ble = new BluetoothLE(MainActivity.this);

        // Get instance of Vibrator from current Context
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        cityZoneKey  = (TextView) findViewById(R.id.textView2);
        cityZone = (TextView) findViewById(R.id.cityZone);
        txtLat = (TextView) findViewById(R.id.latLong);
        resultTextView = (TextView) findViewById(R.id.resultTextView);

        CreateNotificationChannel();
        // ReadData();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        onOffSwitch = (Switch) findViewById(R.id.switch1);
        distancing = (Switch) findViewById(R.id.distancing);


        // set the current state of a Switch
        ReadData();
        onOffSwitch.setChecked(false);
        onOffSwitch.setText("GPS Tracking");
        onOffSwitch.setOnCheckedChangeListener(this);

        distancing.setChecked(false);
        distancing.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                    ble.start();
                else
                    ble.stop();
                    vibrator.cancel();
            }
        });


   /*     fireStoreList = findViewById(R.id.firestore_list);
        Query query = db.collection("city");
        FirestoreRecyclerOptions<CityModel> options = new FirestoreRecyclerOptions.Builder<CityModel>()
                .setQuery(query, CityModel.class).build();

        adapter = new FirestoreRecyclerAdapter<CityModel, CityViewHolder>(options) {
            @NonNull
            @Override
            public CityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_city_single, parent, false);
                return new CityViewHolder(view);
            }

            @Override
            protected void onBindViewHolder(@NonNull CityViewHolder holder, int position, @NonNull CityModel model) {
                cityModelMap.put(model.getName(), model);
                holder.city_name.setText(model.getName());
                holder.city_cases.setText(model.getCases() + "");
                if (model.getLevel() == 2) {
                    holder.mConstraintLayout.setBackgroundColor(Color.RED);
                } else if (model.getLevel() == 1) {
                    holder.mConstraintLayout.setBackgroundColor(Color.YELLOW);
                } else {
                    holder.mConstraintLayout.setBackgroundColor(Color.GREEN);
                }


            }
        };

        fireStoreList.setHasFixedSize(true);
        fireStoreList.setLayoutManager(new LinearLayoutManager(this));
        fireStoreList.setAdapter(adapter);*/

    }

    @Override
    public void onLocationChanged(Location location) {
        txtLat.setText("Latitude:" + location.getLatitude() + ", Longitude:" + location.getLongitude());
        try {
            GetUserAddress(location.getLatitude(), location.getLongitude(), 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    private class CityViewHolder extends RecyclerView.ViewHolder {
        private TextView city_name;
        private TextView city_cases;
        private ConstraintLayout mConstraintLayout;

        public CityViewHolder(@NonNull View itemView) {
            super(itemView);
            city_name = itemView.findViewById(R.id.city_name);
            city_cases = itemView.findViewById(R.id.city_cases);
            mConstraintLayout = itemView.findViewById(R.id.constraintLayout_list_city_single);

        }
    }

    private void GetUserLocation() {
        final int LOCATION_INTERVAL = 1000;


        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ble.grantLocationPermission();
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1800000, 0, (LocationListener) this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        if (requestCode == BluetoothLE.PERMISSION_ACCESS_FINE_LOCATION){
            if (grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                }
            }else{
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void GetUserAddress(double latitude, double longitude, int maxResults) throws IOException {
        Geocoder gCoder = new Geocoder(this);
        ArrayList<Address> addresses = (ArrayList<Address>) gCoder.getFromLocation(latitude, longitude, 1);
        if (addresses != null && addresses.size() > 0) {
            Address address = addresses.get(0);
            Toast.makeText(this, "country: " + address.getLocality(), Toast.LENGTH_LONG).show();
            Log.d(TAG, address.getLocality());
            Log.d(TAG, address.toString());
            CityModel cityModel = cityModelMap.get(address.getLocality());
            if(null != cityModel) {
                if (cityModel.getLevel() == 2 || cityModel.getLevel() == 1) {
                    CreateNotification("Warning", "You are in a RED zone area");
                } else {
                    CreateNotification("Relax", "You are in a Green zone area");
                }

//            cityZoneKey.setText("City Zone");
                if (cityModel.getLevel() == 2) {
                    cityZone.setText(cityModel.getName() + " - " + "RED Area");
                    cityZone.setBackgroundColor(Color.RED);
                } else if (cityModel.getLevel() == 1) {
                    cityZone.setText(cityModel.getName() + " - " + "YELLOW Area");
                    cityZone.setBackgroundColor(Color.YELLOW);
                } else {
                    cityZone.setText(cityModel.getName() + " - " + "GREEN Area");
                    cityZone.setBackgroundColor(Color.GREEN);
                }
            }else {
                CityModel newCityModel = new CityModel();
                newCityModel.setName(address.getLocality());
                newCityModel.setCases((long) 0);
                newCityModel.setLevel((long) 0);
                this.cityModelMap.put(newCityModel.getName(), newCityModel);
                AddData(newCityModel);
            }


        }
    }

    @Override
    protected void onStop() {
        super.onStop();
       // adapter.stopListening();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // adapter.startListening();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            // do something when check is selected
            Log.d(TAG, " check is selected");
            // CreateNotificationChannel();
            GetUserLocation();
            // ble.start();
        } else {
            //do something when unchecked
            Log.d(TAG, "check is unselected");
            locationManager.removeUpdates(this);
           // ble.stop();

        }
    }

    private void AddData(CityModel cityModel) {
        Log.d(TAG, "AddData");

        Map<String, Object> zone = new HashMap<>();
        zone.put("name", cityModel.getName());
        zone.put("level", cityModel.getLevel());
        zone.put("cases", cityModel.getCases());
        zone.put("createdData", new Date());

        zoneStatusCollection
                .add(zone)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.getId());
                        //ReadData();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding document", e);
                    }
                });

    }


    private void ReadData() {
        Log.d(TAG, "ReadData");

        zoneStatusCollection
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                Log.d(TAG, document.getId() + " => " + document.getData());
                                CityModel cityModel = new CityModel();
                                cityModel.setLevel((Long) document.getData().get("level"));
                                cityModel.setCases((Long)document.getData().get("cases"));
                                cityModel.setName((String)document.getData().get("name"));

                                cityModelMap.put(cityModel.getName(), cityModel);
                            }
                        } else {
                            Log.w(TAG, "Error getting documents.", task.getException());
                        }
                    }
                });
    }


    private void CreateNotificationChannel() {
        // This is the Notification Channel ID. More about this in the next section
        final String NOTIFICATION_CHANNEL_ID = "channel_id";

        //User visible Channel Name
        final String CHANNEL_NAME = "Notification Channel";

        // Importance applicable to all the notifications in this Channel
        int importance = NotificationManager.IMPORTANCE_DEFAULT;

        //Notification channel should only be created for devices running Android 26
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, CHANNEL_NAME, importance);

            //Boolean value to set if lights are enabled for Notifications from this Channel
            notificationChannel.enableLights(true);

            //Boolean value to set if vibration are enabled for Notifications from this Channel
            notificationChannel.enableVibration(true);

            //Sets the color of Notification Light
            notificationChannel.setLightColor(Color.GREEN);

            //Set the vibration pattern for notifications. Pattern is in milliseconds with the format {delay,play,sleep,play,sleep...}
            notificationChannel.setVibrationPattern(new long[]{
                    500,
                    500,
                    500,
                    500,
                    500
            });

            //Sets whether notifications from these Channel should be visible on Lockscreen or not
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannel);

            // CreateNotification();
        }

    }

    private void CreateNotification(String title, String text ) {
        // This is the Notification Channel ID. More about this in the next section
        final String NOTIFICATION_CHANNEL_ID = "channel_id";

        //Notification Channel ID passed as a parameter here will be ignored for all the Android versions below 8.0
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);

        builder.setContentTitle(title);
        builder.setContentText(text);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info);
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_dialog_info));
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);

        /*
        setSound() takes in a Uri as the paramater. In this example I am using predefined Alarm Uri from Ringtone.
        You can set any custom sound by just providing the Uri

        */
        builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));

        /*
        setVibrate takes in a long array which represents the vibrate pattern.
        Its a {delay,vibrate,sleep,vibrate,sleep.....} pattern. The values are to be entered in milliseconds
        */
        builder.setVibrate(new long[] {
                500,
                500,
                500,
                500
        });


        // This intent will be fired when the notification is tapped
        Intent intent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 1001, intent, 0);

        // Following will set the tap action
        builder.setContentIntent(pendingIntent);


        Notification notification = builder.build();
        IssuingNotification(notification);
    }

    private void IssuingNotification(Notification notification) {
        // Unique identifier for notification
        int NOTIFICATION_ID = 101;

        //This is what will will issue the notification i.e.notification will be visible
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(this);
        notificationManagerCompat.notify(NOTIFICATION_ID, notification);
    }

    private void Vibration () {

        // Start without a delay
        // Vibrate for 100 milliseconds
        // Sleep for 5000 milliseconds
        long[] pattern = {0, 50, 5000};

        // The '0' here means to repeat indefinitely
        // '0' is actually the index at which the pattern keeps repeating from (the start)
        // To repeat the pattern from any other point, you could increase the index, e.g. '1'
        vibrator.vibrate(pattern, 0);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void bleScanActivity(Map<String, List<Object>> deviceList){
        Log.d(getString(R.string.tag_ble), "bleScanActivity");

        //Display all the devices in the device list
        StringBuilder displayText = new StringBuilder();
        displayText.setLength(0);
        for(Map.Entry<String, List<Object>> entry : deviceList.entrySet()) {
            if (((int)entry.getValue().get(BluetoothLE.INDEX_COUNTER)) != 0) {
                displayText.append("\n");
                displayText.append( entry.getKey());
                displayText.append("\n");
                displayText.append(entry.getValue().get(BluetoothLE.INDEX_IS_SAFE));
                displayText.append("\n");
                displayText.append(entry.getValue().get(BluetoothLE.INDEX_NAME)); //For testing purposes only
                displayText.append("\n");
                displayText.append(entry.getValue().get(BluetoothLE.INDEX_AVG_RSSI)); //For testing purposes only
                displayText.append("\n");
                displayText.append(entry.getValue().get(BluetoothLE.INDEX_DISTANCE)); //For testing purposes only
                displayText.append("\n");
                displayText.append("----------------------------"); //For testing purposes only
                if(!vibrator.hasVibrator()){
                    Log.i("hasVibrator", ""); //Display result here
                }

                if(entry.getValue().get(BluetoothLE.INDEX_IS_SAFE).equals("UNSAFE")){
//                    if(!vibrating){
//                        vibrating = true;
//                        Vibration();
//                        Handler handler = new Handler();
//                        handler.postDelayed(new Runnable() {
//                            public void run() {
//                                // yourMethod();
//                                vibrating = false;
//                            }
//                        }, 3000);
//                    }
                    Vibration();

                }else {
                    vibrator.cancel();
                }
            }
        }

        //Display in the text view
        if (displayText.length() > 0) {
            resultTextView.setText(displayText);
            Log.i("displayText.toString()", displayText.toString()); //Display result here
        } else{
            resultTextView.setText("No Device found!");
            Log.i("displayText.toString()", "No Device found!");
        }
    }

}

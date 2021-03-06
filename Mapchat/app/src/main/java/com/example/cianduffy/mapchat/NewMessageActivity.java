package com.example.cianduffy.mapchat;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Cian on 16/12/2016.
 */

public class NewMessageActivity extends AppCompatActivity  implements LocationListener {

    EditText newMessageEditText;

    private LocationManager locationManager;
    private Location lastKnownLocation;
    private String messageText;
    private DatabaseReference database;

    HashMap<String, MessageLocation> existingLocations;
    private int MIN_DISTANCE = 10; //meters

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Setup View
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_message);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        newMessageEditText = (EditText) findViewById(R.id.new_message_edit_text);

        // Get reference to Firebase DB
        database = FirebaseDatabase.getInstance().getReference();
        // setup list of existing locations and LocationManager
        setupExistingLocations();
        setupLocationManager();
    }

    private void setupExistingLocations() {
        // Populates HashMap with MessageLocation Objects from Firebase DB
        existingLocations = new HashMap<String, MessageLocation>();
        final DatabaseReference ref = database.child("Locations").getRef();
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot locationSnapshot: dataSnapshot.getChildren()) {
                    MessageLocation location = locationSnapshot.getValue(MessageLocation.class);
                    String key = locationSnapshot.getKey();
                    existingLocations.put(key, location);
                }
                ref.removeEventListener(this);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                System.out.println("The read failed: " + databaseError.getCode());
            }
        });
    }

    private void setupLocationManager() {
        // Get a reference to the system MessageLocation Manager
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        try {
            // Register for location updates
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        }
        catch (SecurityException e) {
            Log.e("GPS", "exception occured " + e.getMessage());
        }
        catch (Exception e) {
            Log.e("GPS", "exception occured " + e.getMessage());
        }
    }

    public void composeMessage(View view) {
        // Stores message text and checks if the message can be uploaded
        this.messageText = newMessageEditText.getText().toString();
        if (canSendMessage()) {
            // Check if there are locations within 10m of the new message and create Message Object
            MessageLocation inRangeLocation = locationsWithin10Meters();
            Message message = createMessage(messageText);

            if(inRangeLocation == null) {
                // If locationWithin10Meters() returns null, a new MessageLocation Object is created
                // and uploaded to Firebase
                MessageLocation location = createLocation(message);
                database.child("Locations").push().setValue(location);
            } else {
                // If locationWithin10Meters() returns a MessageLocation Object, the Message is
                // appended to that MessageLocation in Firebase
                for (Map.Entry<String, MessageLocation> entry : existingLocations.entrySet()) {
                    if (entry.getValue().equals(inRangeLocation)) {
                        inRangeLocation.addMessage(message);
                        database.child("Locations").child(entry.getKey()).setValue(inRangeLocation);
                    }
                }
            }
            // Clear text field
            newMessageEditText.setText("");
            // Update existing locations
            setupExistingLocations();
        } else {
            handleMessageSendError();
        }
    }

    private boolean canSendMessage() {
        // Check if the user has typed a message and that there is a valid location
        return messageText.length() > 0 && lastKnownLocation != null;
    }

    private MessageLocation locationsWithin10Meters() {
        for(MessageLocation location : existingLocations.values()) {
            // create target location
            Location targetLocation = new Location("");
            targetLocation.setLatitude(location.getLatitude());
            targetLocation.setLongitude(location.getLongitude());
            double distance = lastKnownLocation.distanceTo(targetLocation);
            if (distance < MIN_DISTANCE) {
                return location;
            }
        }
        return null;
    }

    public Message createMessage(String text) {
        // Initialise Message Object
        Message message = new Message();

        // Add Message Text
        message.setMessageText(text);

        // Add Timestamp
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }

    private MessageLocation createLocation(Message message) {
        // Creates MessageLocation Object
        double lat = lastKnownLocation.getLatitude();
        double lon = lastKnownLocation.getLongitude();
        ArrayList<Message> messages = new ArrayList<Message>();
        messages.add(message);
        return new MessageLocation(messages, lat, lon);
    }

    private void handleMessageSendError() {
        if (messageText.length() <= 0) {
            Log.e("ERROR", "Message has no content");
        } else if (lastKnownLocation == null) {
            Log.e("ERROR", "Location unknown");
        }
        CharSequence text = "Unable to send message";
        displayToast(text);
    }

    private void displayToast(CharSequence text) {
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    // LocationManager methods
    @Override
    public void onLocationChanged(Location location) {
        lastKnownLocation = location;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}
}
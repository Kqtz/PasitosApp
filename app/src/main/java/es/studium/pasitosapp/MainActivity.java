package es.studium.pasitosapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    // Intervalo de 5 minutos = 300000 ms
    private static final long INTERVAL = 300000;
    private Handler handler;
    private Runnable locationRunnable;

    private DBHelper dbHelper;

    // Botones para acercar y alejar
    private Button zoomInButton, zoomOutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializamos el helper de la base de datos
        dbHelper = new DBHelper(this);

        // Configuramos el fragment del mapa
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Solicitar permiso de localización si no está concedido
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }

        // Inicializamos los botones de zoom
        zoomInButton = findViewById(R.id.zoom_in_button);
        zoomOutButton = findViewById(R.id.zoom_out_button);

        zoomInButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if(mMap != null) {
                    mMap.animateCamera(CameraUpdateFactory.zoomIn());
                }
            }
        });

        zoomOutButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if(mMap != null) {
                    mMap.animateCamera(CameraUpdateFactory.zoomOut());
                }
            }
        });

        // Configuramos la tarea periódica: cada 15 segundos se guarda la ubicación y el nivel de batería
        handler = new Handler(Looper.getMainLooper());
        locationRunnable = new Runnable() {
            @Override
            public void run() {
                saveCurrentLocationAndBattery();
                handler.postDelayed(this, INTERVAL);
            }
        };
        handler.post(locationRunnable);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Activamos la capa "My Location" si se concedió el permiso
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }
        // Cargamos los marcadores existentes desde la base de datos
        loadMarkersFromDB();
    }

    private BitmapDescriptor bitmapDescriptorFromVector(Context context, int vectorResId) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        if (vectorDrawable == null) {
            return BitmapDescriptorFactory.defaultMarker();
        }
        vectorDrawable.setBounds(0, 0, vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(),
                vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private void loadMarkersFromDB() {
        Cursor cursor = dbHelper.getAllLocations();
        if(cursor != null && cursor.moveToFirst()){
            do {
                double lat = cursor.getDouble(cursor.getColumnIndex(DBHelper.COLUMN_LATITUDE));
                double lng = cursor.getDouble(cursor.getColumnIndex(DBHelper.COLUMN_LONGITUDE));
                int battery = cursor.getInt(cursor.getColumnIndex(DBHelper.COLUMN_BATTERY));
                LatLng position = new LatLng(lat, lng);
                mMap.addMarker(new MarkerOptions()
                        .position(position)
                        .title(getString(R.string.marker_title))
                        .snippet(getString(R.string.marker_snippet, battery))
                        .icon(bitmapDescriptorFromVector(this, R.drawable.ic_marker)));
            } while(cursor.moveToNext());
            cursor.close();
        }
    }

    private void saveCurrentLocationAndBattery() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(R.string.permission_not_granted), Toast.LENGTH_SHORT).show();
            return;
        }
        if(mMap != null && mMap.getMyLocation() != null) {
            Location location = mMap.getMyLocation();
            double lat = location.getLatitude();
            double lng = location.getLongitude();
            int batteryLevel = getBatteryLevel();

            long id = dbHelper.insertLocation(lat, lng, batteryLevel);
            if(id != -1) {
                LatLng position = new LatLng(lat, lng);
                mMap.addMarker(new MarkerOptions()
                        .position(position)
                        .title(getString(R.string.marker_title))
                        .snippet(getString(R.string.marker_snippet, batteryLevel))
                        .icon(bitmapDescriptorFromVector(this, R.drawable.ic_marker)));
            } else {
                Log.e("PasitosApp", "Error al insertar la ubicación en la base de datos.");
            }
        } else {
            Toast.makeText(this, getString(R.string.location_not_available), Toast.LENGTH_SHORT).show();
        }
    }

    // Obtiene el nivel de batería actual
    private int getBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int level = 0;
        int scale = 1;
        if (batteryStatus != null) {
            level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        }
        return (int)((level / (float) scale) * 100);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if(mMap != null) {
                    try {
                        mMap.setMyLocationEnabled(true);
                    } catch(SecurityException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                Toast.makeText(this, getString(R.string.permission_not_granted), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(locationRunnable);
    }
}

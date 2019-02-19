package com.webronin_26.picturenoteapp;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.FrameLayout;

public class MainActivity extends AppCompatActivity {

    private int[] permissionArray = { 0 , 0 };
    private final int EXTERNAL_PERMISSION_CODE = 100;
    private final int CAMERA_PERMISSION_CODE = 200;

    private FrameLayout galleryFrameLayout = null;
    private FrameLayout cameraFrameLayout = null;
    private Toolbar toolbar = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.activity_main);
        checkPermissions();
        initView();
    }

    private void initView() {

        toolbar = findViewById( R.id.main_tool_bar );
        setSupportActionBar( toolbar );
        galleryFrameLayout = findViewById( R.id.gallery_frame_layout );
        cameraFrameLayout = findViewById( R.id.camera_frame_layout );

        galleryFrameLayout.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if( permissionArray[0] == 1 ) {
                    Intent intent = new Intent( MainActivity.this , PictureInformationActivity.class );
                    startActivity( intent );
                }else {
                    askExternalPermission();
                }
            }
        } );

        cameraFrameLayout.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if( permissionArray[1] == 1 ) {
                    Intent intent = new Intent( MainActivity.this , CameraActivity.class );
                    startActivity( intent );
                }else {
                    askCameraPermission();
                }
            }
        } );

    }

    private void checkPermissions() {
        if( Build.VERSION.SDK_INT >= 23 ) {
            if ( ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE ) ==
                    PackageManager.PERMISSION_GRANTED ) {
                permissionArray[0] = 1;
            }
            if ( ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA ) ==
                    PackageManager.PERMISSION_GRANTED ) {
                permissionArray[1] = 1;
            }
        }else {
            permissionArray[0] = 1;
            permissionArray[1] = 1;
        }
    }

    private void askExternalPermission() {

        if( permissionArray[0] == 0 ) {

            if ( ActivityCompat.shouldShowRequestPermissionRationale(
                    this ,  Manifest.permission.WRITE_EXTERNAL_STORAGE ) ) {

                new AlertDialog.Builder( this )
                        .setMessage( "需要照片權限來打開您的相冊" )
                        .setPositiveButton("瞭解", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick( DialogInterface dialog, int which ) {
                                ActivityCompat.requestPermissions( MainActivity.this
                                        , new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE } , EXTERNAL_PERMISSION_CODE );
                            }
                        }).show();

            } else {

                new AlertDialog.Builder( this )
                        .setMessage( "需要照片權限來打開您的相冊" )
                        .setPositiveButton("瞭解", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick( DialogInterface dialog, int which ) {
                                ActivityCompat.requestPermissions( MainActivity.this
                                        , new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE } , EXTERNAL_PERMISSION_CODE );
                            }
                        }).show();

            }

        }

    }

    private void askCameraPermission() {

        if( permissionArray[1] == 0 ) {

            if ( ActivityCompat.shouldShowRequestPermissionRationale(
                    this ,  Manifest.permission.CAMERA ) ) {

                new AlertDialog.Builder( this )
                        .setMessage( "需要相機權限來照相" )
                        .setPositiveButton("瞭解", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick( DialogInterface dialog, int which ) {
                                ActivityCompat.requestPermissions( MainActivity.this
                                        , new String[]{ Manifest.permission.CAMERA } , CAMERA_PERMISSION_CODE );
                            }
                        }).show();

            } else {

                new AlertDialog.Builder( this )
                        .setMessage( "需要相機權限來照相" )
                        .setPositiveButton("瞭解", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick( DialogInterface dialog, int which ) {
                                ActivityCompat.requestPermissions( MainActivity.this
                                        , new String[]{ Manifest.permission.CAMERA } , CAMERA_PERMISSION_CODE );
                            }
                        }).show();

            }

        }

    }

    @Override
    public void onRequestPermissionsResult( int requestCode , String permissions[] , int[] grantResults ) {

        switch ( requestCode ) {

            case EXTERNAL_PERMISSION_CODE: {
                if ( grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionArray[0] = 1;
                } else {
                    permissionArray[0] = 0;
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            , Uri.parse("package:" + getPackageName()));
                    intent.addCategory(Intent.CATEGORY_DEFAULT);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
                return;
            }

            case  CAMERA_PERMISSION_CODE:{
                if ( grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionArray[1] = 1;
                } else {
                    permissionArray[1] = 0;
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            , Uri.parse("package:" + getPackageName()));
                    intent.addCategory(Intent.CATEGORY_DEFAULT);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
                return;
            }
        }

    }

}

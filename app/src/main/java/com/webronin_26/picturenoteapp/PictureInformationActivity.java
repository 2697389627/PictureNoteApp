package com.webronin_26.picturenoteapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.Toolbar;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

public class PictureInformationActivity extends AppCompatActivity {

    private final int READY_TO_PREVIEW = 0;
    private final int EDITING_INFORMATIONS = 1;
    private final int PREVIEW = 2;

    private int state = READY_TO_PREVIEW;

    private AppCompatEditText appCompatEditText = null;
    private AppCompatTextView appCompatTextView = null;
    private FloatingActionButton saveFloatingActionButton = null;
    private LinearLayout informationLinearLayout = null;
    private Toolbar pictureInformationToolBar = null;
    private ImageView imageView = null;

    private final int PHOTO_REQUEST_CODE = 10;
    private SharedPreferences sharedPreferences = null;
    private String currentFilePath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.picture_information_layout);

        initView();
        initData();

        Intent intent = new Intent( Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PHOTO_REQUEST_CODE);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        try{

            if( requestCode == PHOTO_REQUEST_CODE ) {

                if( data != null ) {
                    Uri selectedImage = data.getData();
                    String[] filePathColumn = { MediaStore.Images.Media.DATA };
                    Cursor cursor = getContentResolver().query(
                            selectedImage, filePathColumn, null, null, null);
                    cursor.moveToFirst();
                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    currentFilePath = cursor.getString(columnIndex);
                    cursor.close();

                    imageView.setImageBitmap(BitmapFactory.decodeFile( currentFilePath ));
                    setInformation( currentFilePath );
                } else {
                    currentFilePath = "";
                }
            }
        } catch ( Exception e ) {
            Log.e( "-------" , e.toString() );
        }
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item ) {

        switch ( item.getItemId() ){
            case android.R.id.home:
                Intent intent = new Intent( Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, PHOTO_REQUEST_CODE);
                break;
            case R.id.camera:
                startActivity( new Intent( PictureInformationActivity.this , CameraActivity.class ) );
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate( R.menu.picture_activity_menu , menu );
        return super.onCreateOptionsMenu(menu);

    }

    private void initView() {

        appCompatTextView = findViewById( R.id.text_view );
        appCompatEditText = findViewById( R.id.edit_text );
        saveFloatingActionButton = findViewById( R.id.save_button );
        informationLinearLayout = findViewById( R.id.information_linear_layout );
        pictureInformationToolBar = findViewById( R.id.picture_information_toolbar );
        imageView = findViewById( R.id.image_view );

        informationLinearLayout.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if( currentFilePath.length() != 0 ) {

                    appCompatEditText.setVisibility( View.VISIBLE );
                    appCompatEditText.setFocusable( true );
                    appCompatEditText.setFocusableInTouchMode( true );
                    appCompatEditText.requestFocus();

                    if( "請輸入註解".equals( appCompatTextView.getText() ) ) {
                        appCompatEditText.setText( "" );
                    }else {
                        appCompatEditText.setText( appCompatTextView.getText() );
                    }

                    appCompatTextView.setVisibility( View.GONE );
                    state = EDITING_INFORMATIONS;
                }
            }
        } );

        saveFloatingActionButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch ( state ){
                    case EDITING_INFORMATIONS :
                        appCompatTextView.setVisibility( View.VISIBLE );
                        String currentString = appCompatEditText.getText().toString();
                        if( currentString.length() != 0 ) {
                            appCompatTextView.setText( appCompatEditText.getText() );
                            saveInformation( currentFilePath , currentString );
                            Toast.makeText( PictureInformationActivity.this , "儲存修改" , Toast.LENGTH_SHORT ).show();
                        }

                        appCompatTextView.setFocusable( true );
                        appCompatTextView.setFocusableInTouchMode( true );
                        appCompatTextView.requestFocus();
                        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        inputMethodManager.hideSoftInputFromWindow(informationLinearLayout.getWindowToken(), 0);

                        appCompatEditText.setVisibility( View.GONE );
                        state = PREVIEW;
                        break;
                }
            }
        } );

        setSupportActionBar( pictureInformationToolBar );
        ActionBar actionBar = getSupportActionBar();
        if( actionBar != null ) {
            actionBar.setTitle( "回相冊" );
            actionBar.setDisplayHomeAsUpEnabled( true );
            actionBar.setHomeAsUpIndicator( R.drawable.left_icon );
        }
    }

    private void initData() {

        sharedPreferences = getSharedPreferences( "PictureNoteAppInformation" , MODE_PRIVATE );

    }

    private void saveInformation( String filePath , String photoInfo ) {

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString( filePath , photoInfo ).apply();

    }

    private void setInformation( String filePath ) {

        String currentInfo = sharedPreferences.getString( filePath , "" );

        if( currentInfo.length() == 0 ) {
            appCompatTextView.setText( "請輸入註解" );
        }else {
            appCompatTextView.setText( currentInfo );
        }
    }

}

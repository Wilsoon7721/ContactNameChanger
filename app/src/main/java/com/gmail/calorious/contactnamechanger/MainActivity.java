package com.gmail.calorious.contactnamechanger;

import android.Manifest;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static long LAST_SCROLL_TIME;
    private static final int CONTACTS_INTENT_ID = 17571;
    private static final int CONTACTS_PERMISSION_REQUEST_CODE = 172163;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 123736;
    private boolean contactSelection = false;
    private Button pickContactButton;
    private EditText contactTextBox;
    private TextView outputLog;
//    private TextView multipleNumbersNotice;
    private ImageView contactImage;
    private final LinkedList<String> queue = new LinkedList<>();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private String contactId;

    private final static String[] DATA_COLS = {

            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,//phone number
            ContactsContract.Data.CONTACT_ID
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main_activity);
        Objects.requireNonNull(getSupportActionBar()).hide();
        pickContactButton = findViewById(R.id.pick_contact_button);
        contactImage = findViewById(R.id.contact_image);
        //multipleNumbersNotice = findViewById(R.id.multipleNumbersNotice);
        contactTextBox = findViewById(R.id.new_contact_name_field);
        outputLog = findViewById(R.id.output_log);
        if(!InternalStorage.initialized)
            InternalStorage.initialize(getApplicationContext(), getFilesDir());
        String[] contactsPermissions = {Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS};
        String[] storagePermissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, contactsPermissions, CONTACTS_PERMISSION_REQUEST_CODE);
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, storagePermissions, STORAGE_PERMISSION_REQUEST_CODE);
        }
        Runnable outputLog = () -> {
           if(!(queue.isEmpty())) {
                String text = queue.get(0);
                writeToOutputLog(text);
                queue.remove(text);
           }
        };
        scheduledExecutorService.scheduleAtFixedRate(outputLog, 0, 2, TimeUnit.SECONDS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == CONTACTS_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "This application cannot function without the READ_CONTACTS permission.", Toast.LENGTH_LONG).show();
                onDestroy();
                return;
            }
            Toast.makeText(this, "Successfully granted Contacts permission!", Toast.LENGTH_SHORT).show();
        }
        if(requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if(Arrays.stream(grantResults).allMatch(i -> i == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Successfully granted storage permission!", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "This application cannot function without the STORAGE permission.", Toast.LENGTH_LONG).show();
            onDestroy();
        }
    }

    // View object to mitigate 'incorrect signature' as method is linked to button
    @SuppressWarnings("deprecation")
    public void pickContact(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        startActivityForResult(intent, CONTACTS_INTENT_ID);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode != CONTACTS_INTENT_ID) return;
        /*
        if(resultCode != RESULT_OK) {
            Intent reopenApp = new Intent(this, MainActivity.class);
            reopenApp.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivityIfNeeded(reopenApp, 0);
            writeToOutputLog("ERR_ACTION_CANCELLED while picking contact.");
            return;
        }
         */
        Cursor cursor1;
        Uri uri = null;
        if (data != null) {
            uri = data.getData();
        }
        cursor1 = getContentResolver().query(uri, null, null, null, null);
        if(cursor1.moveToFirst()) {
            contactId = cursor1.getString(cursor1.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
            String contactName = cursor1.getString(cursor1.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)), systemContactImage = cursor1.getString(cursor1.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI));
            String phoneNumberResults = cursor1.getString(cursor1.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER));
            String firstContactNumber = null;
            int holdingResult = Integer.parseInt(phoneNumberResults);
            if (holdingResult == 1) {
                Cursor result = managedQuery(ContactsContract.Contacts.CONTENT_URI, null, ContactsContract.Contacts._ID +" = ?", new String[] {contactId}, null);
                if(result.moveToFirst()) {
                    Cursor c = getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                            new String[] {ContactsContract.Data._ID, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.LABEL},
                            ContactsContract.Data.RAW_CONTACT_ID + "=?" + " AND "
                                    + ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'",
                            new String[] {contactId}, null);
                    if(c.moveToFirst()) {
                        int phoneColumn = c.getColumnIndexOrThrow("data1");
                        firstContactNumber = c.getString(phoneColumn);
                        if(firstContactNumber.isEmpty())
                            firstContactNumber = "ERR 404"; // Error is this code's end, perhaps the obtaining method is wrong. You should really fix it.
                    }
                    c.close();
                }
            }
            if (systemContactImage != null) {
                contactImage.setImageURI(Uri.parse(systemContactImage));
            } else {
                contactImage.setImageDrawable(ResourcesCompat.getDrawable(getResources(), android.R.drawable.ic_menu_gallery, null));
            }
            String format = contactName + " | " + firstContactNumber;
            pickContactButton.setText(format);
        }
        cursor1.close();
        contactSelection = true;
    }

    // View object mitigates 'incorrect signature', this method is linked to button.
    public void applyChanges(View view) {
        if(!contactSelection) {
            Snackbar.make(view, "You need to select a contact first!", Snackbar.LENGTH_SHORT).show();
            return;
        }
        if (contactTextBox.getText().length() == 0 || contactTextBox.getText() == null || contactTextBox.getText().toString().equals("") || contactTextBox.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Changes could not be made as the new contact name field is blank.", Toast.LENGTH_LONG).show();
            writeToOutputLog("Could not apply changes.");
            return;
        }
        writeToOutputLog("Applying changes, please wait...");
        String newName = contactTextBox.getText().toString();
        String where = String.format(
                "%s = '%s' AND %s = ?",
                DATA_COLS[0], //mimetype
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                DATA_COLS[2]/*contactId*/);
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        String[] args = {contactId};
        operations.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI).withSelection(where, args).withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, newName).build());
        try {
            ContentProviderResult[] results = getContentResolver().applyBatch(ContactsContract.AUTHORITY, operations);
            for(ContentProviderResult result : results) {
                Log.d("MainActivity - applyChanges - Result", result.toString());
                writeToOutputLog("Change result: " + result);
            }
        } catch (Exception ex) {
            Log.e("MainActivity - applyChanges", "Exception occurred while applying changes.");
            writeToOutputLog("Error occurred while applying changes - ContentResolver could not use Contacts AUTHORITY to complete operation.");
            ex.printStackTrace();
            return;
        }
        Snackbar.make(view, "Successfully changed name!", Snackbar.LENGTH_SHORT).show();
        pickContactButton.setText(R.string.pick_contact_button_text);
        contactTextBox.setText("");
        contactImage.setImageDrawable(ResourcesCompat.getDrawable(getResources(), android.R.drawable.ic_menu_gallery, null));
    }


    private void writeToOutputLog(CharSequence text) {
        if(LAST_SCROLL_TIME != 0L && (System.currentTimeMillis() - LAST_SCROLL_TIME) >= 3000L) {
            outputLog.setText(text);
            LAST_SCROLL_TIME = System.currentTimeMillis();
            return;
        }
        queue.add(text.toString());
    }
}
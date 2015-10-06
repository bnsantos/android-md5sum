package com.bnsantos.md5sum;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    private static final int SELECT_FILE_CODE = 333;
    @Bind(R.id.filePath) TextView path;
    @Bind(R.id.fileHash) TextView hash;
    @Bind(R.id.timeSpent) TextView timeSpent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @OnClick(R.id.pickFile)
    public void pickFile(){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, getString(R.string.pick_file_chooser)), SELECT_FILE_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if(resultCode==RESULT_OK&&requestCode==SELECT_FILE_CODE){
            long start = System.currentTimeMillis();
            String filepath = getPathFromIntent(intent);
            if(filepath!=null){
                path.setText(filepath);
                String md5 = calculateMd5Sum(filepath);
                hash.setText(md5);
            }
            long finish = System.currentTimeMillis();
            timeSpent.setText(Long.toString(finish-start) + " millis");
        }
    }

    private String calculateMd5Sum(String filepath){
        File file = new File(filepath);
        if(file.isFile()){
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                String digest = getDigest(new FileInputStream(file), md, 2048);
                return digest;
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public String getDigest(InputStream is, MessageDigest md, int byteArraySize) throws NoSuchAlgorithmException, IOException {

        md.reset();
        byte[] bytes = new byte[byteArraySize];
        int numBytes;
        while ((numBytes = is.read(bytes)) != -1) {
            md.update(bytes, 0, numBytes);
        }
        byte[] digest = md.digest();
        return toHexadecimal(digest);
    }

    private static String toHexadecimal(byte[] digest){
        String hash = "";
        for(byte aux : digest) {
            int b = aux & 0xff;
            if (Integer.toHexString(b).length() == 1) hash += "0";
            hash += Integer.toHexString(b);
        }
        return hash;
    }

    private String getPathFromIntent(Intent intent) {
        if (intent != null) {
            Uri pictureUri = null;
            if (intent.getData() != null) {
                pictureUri = intent.getData();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                if (intent.getClipData() != null && intent.getClipData().getItemCount() == 1) {
                    pictureUri = intent.getClipData().getItemAt(0).getUri();
                }
            }
            if (pictureUri != null) {
                return getRealPath(this, pictureUri);
            }
        }
        return null;
    }

    private String getRealPath(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }

        if (isLocalFile(uri)) {
            return uri.getPath();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (isExternalStorageDocument(uri)) {
                return extractExternalStorageDocument(uri);
            } else if (isDownloadsDocument(uri)) {
                return extractDownloadsDocument(context, uri);
            } else if (isMediaDocument(uri)) {
                return extractMedia(context, uri);
            } else if (isGoogleDriveStorage(uri) || isGooglePhotosStorage(uri)) {
                return null;
            } else {
                return getRealPathFromURI_API11to18(context, uri);
            }
        } else {
            return getRealPathFromURI_API11to18(context, uri);
        }
    }

    @SuppressLint("NewApi")
    private String extractExternalStorageDocument(Uri uri) {
        final String docId = DocumentsContract.getDocumentId(uri);
        final String[] split = docId.split(":");
        final String type = split[0];

        if ("primary".equalsIgnoreCase(type)) {
            return Environment.getExternalStorageDirectory() + "/" + split[1];
        }
        return null;
    }

    @SuppressLint("NewApi")
    private String extractDownloadsDocument(Context context, Uri uri) {
        final String id = DocumentsContract.getDocumentId(uri);
        final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
        return getDataColumn(context, contentUri, null, null);
    }

    @SuppressLint("NewApi")
    private String extractMedia(Context context, Uri uri) {
        String wholeID = DocumentsContract.getDocumentId(uri);
        // Split at colon, use second item in the array
        String id = wholeID.split(":")[1];

        // where id is equal to
        String sel = MediaStore.Images.Media._ID + "=?";

        return getDataColumn(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, sel, new String[]{id});
    }

    @SuppressLint("NewApi")
    private String getRealPathFromURI_API11to18(Context context, Uri contentUri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        String result = null;

        CursorLoader cursorLoader = new CursorLoader(context, contentUri, projection, null, null, null);
        Cursor cursor = cursorLoader.loadInBackground();
        try {
            if (cursor != null) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                result = cursor.getString(column_index);
            }
            return result;
        } catch (IllegalArgumentException e) {
            Log.e(MainActivity.class.getSimpleName(), "Error getting path from URI", e);
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = MediaStore.Images.Media.DATA;
        final String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } catch (IllegalArgumentException e) {
            Log.e(MainActivity.class.getSimpleName(), "Error getting path from URI", e);
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     *
     * @param uri
     * @return
     */

    private static boolean isLocalFile(Uri uri) {
        return "file".equals(uri.getScheme());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isGoogleDriveStorage(Uri uri) {
        return "com.google.android.apps.docs.storage".equals(uri.getAuthority()) || "com.google.android.apps.docs.files".equals(uri.getAuthority());
    }

    public static boolean isGooglePhotosStorage(Uri uri) {
        return "com.google.android.apps.photos.contentprovider".equals(uri.getAuthority());
    }
}

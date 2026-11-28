package app.terra.test.sharereceiver;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Test-only destination. All bytes stay in this application's private directory. */
public class ReceiveActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setText("Reading shared file");
        setContentView(text);
        new Thread(() -> {
            try {
                Intent intent = getIntent();
                Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri == null || !"content".equals(uri.getScheme())) throw new IllegalArgumentException("Missing URI");
                String name;
                try (Cursor rows = getContentResolver().query(uri,new String[] {OpenableColumns.DISPLAY_NAME},null,null,null)) {
                    if (rows == null || !rows.moveToFirst()) throw new IllegalStateException("Missing document name");
                    name = rows.getString(0);
                }
                File payload = new File(getFilesDir(),"payload");
                try (InputStream input = getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(payload)) {
                    byte[] block = new byte[16384];
                    for (int count; (count=input.read(block)) != -1;) output.write(block,0,count);
                }
                JSONObject received = new JSONObject().put("mime",intent.getType()).put("name",name)
                    .put("subject",intent.getStringExtra(Intent.EXTRA_SUBJECT))
                    .put("providerMime",getContentResolver().getType(uri)).put("bytes",payload.length())
                    .put("readGrant",(intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
                    .put("clipMatches",intent.getClipData()!=null && uri.equals(intent.getClipData().getItemAt(0).getUri()));
                try (FileOutputStream output = openFileOutput("receipt.json",MODE_PRIVATE)) {
                    output.write(received.toString().getBytes(StandardCharsets.UTF_8));
                }
                runOnUiThread(() -> text.setText("Terra file received"));
            } catch (Exception error) {
                runOnUiThread(() -> text.setText("Receive failed — "+error.getClass().getSimpleName()));
            }
        }).start();
    }
}

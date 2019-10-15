package com.example.o4s;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class ResponseActivity extends AppCompatActivity {
    TextView textView;
    //ArrayList<String> response=new ArrayList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_response);
        textView=findViewById(R.id.data);


        Bundle gt=getIntent().getExtras();
        //str=gt.getString("key");
        Object oresponse= gt.get("buffer");

        String[] s=oresponse.toString().split(",");
        for(String s1:s){
            textView.append("\n\n\n"+s1);

        }
        // textView.setText();
        //Toast.makeText(this,str,Toast.LENGTH_LONG).show();

    }

    @Override
    public void onBackPressed() {
        Intent mainIntent = new Intent(this, QrcodeActivity.class);
        this.startActivity(mainIntent);
    }
}

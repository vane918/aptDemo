package com.vanelst.aptdemo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.vanelst.annotation.ARouter;

@ARouter(path = "/app/SecondActivity")
public class SecondActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);
    }

    public void jumpTo(View view) {
        Intent intent = new Intent(this, MainActivity$ARouter.findTargetClass("/app/MainActivity"));
        startActivity(intent);
    }
}

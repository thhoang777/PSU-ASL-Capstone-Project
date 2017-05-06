package com.psu.capstonew17.backend.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

import com.psu.capstonew17.backend.api.*;


import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

import com.psu.capstonew17.backend.db.AslDbContract.*;
import com.psu.capstonew17.backend.db.AslDbHelper;
import com.psu.capstonew17.backend.video.PreprocessingPipeline;


public class ExternalVideoManager implements VideoManager {
    static ExternalVideoManager INSTANCE = new ExternalVideoManager();

    private AslDbHelper dbHelper;

    public static VideoManager getInstance(Context context){
        INSTANCE.dbHelper = new AslDbHelper(context);
        return INSTANCE;
    }

    AslDbHelper getDbHelper(){
        return dbHelper;
    }

    Video getVideo(int id){
        return null;
    }


    @Override
    public void importVideo(final File videoFile, ImportOptions options, VideoImportListener handler) {
        final VideoImportListener handle = handler;

        // generate an output file location
        final File outFile = new File(Environment.getDataDirectory().getAbsoluteFile(),
                UUID.randomUUID().toString());

        PreprocessingPipeline  pipeline;
        try {
            pipeline = new PreprocessingPipeline(outFile, videoFile);
        } catch(IOException e) {
            handler.onFailed(e);
            return;
        }

        pipeline.setListener(new PreprocessingPipeline.PreprocessingListener() {
            @Override
            public void onCompleted() {
                MessageDigest digest;
                try {
                    digest = MessageDigest.getInstance("SHA256");
                } catch(NoSuchAlgorithmException e) {
                    Log.d("VideoManager", "System does not support SHA256 hash", e);
                    outFile.delete();
                    handle.onFailed(e);
                    return;
                }

                byte[] sha = null;
                try {
                    InputStream istrm = new FileInputStream(outFile);
                    byte[] arr = new byte[8192];
                    int len;
                    while((len = istrm.read(arr)) > 0) {
                        digest.update(arr, 0, len);
                    }
                    sha = digest.digest();
                } catch(IOException e) {
                    Log.d("VideoManager", "Unexpected IO error", e);
                    outFile.delete();
                    handle.onFailed(e);
                    return;
                }
                // check if video already exists, if so delete the video file we just created
                dbHelper = ExternalVideoManager.INSTANCE.getDbHelper();
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                String query = dbHelper.buildSelectQuery(
                        VideoEntry.TABLE_NAME,
                        Arrays.asList(VideoEntry.COLUMN_SHA + "='" + sha.toString() + "'")
                );
                Video video;
                Cursor cursor = db.rawQuery(query, null);
                if(cursor.moveToFirst()){
                    int videoId = cursor.getInt(cursor.getColumnIndex(VideoEntry.COLUMN_ID));
                    String videoPath = cursor.getString(cursor.getColumnIndex(VideoEntry.COLUMN_PATH));
                    File videoFile = new File(videoPath);
                    outFile.delete();
                    video = new ExternalVideo(videoId, videoFile.getAbsoluteFile());
                }
                else{
                    // create the new video
                    ContentValues values = new ContentValues();
                    values.put(VideoEntry.COLUMN_PATH, outFile.getAbsolutePath());
                    values.put(VideoEntry.COLUMN_SHA, sha.toString());
                    int videoId = (int) db.insert(VideoEntry.TABLE_NAME, null, values);
                    video = new ExternalVideo(videoId, outFile.getAbsoluteFile());
                }
                cursor.close();
                handle.onComplete(video);
            }

            @Override
            public void onFailed() {
                handle.onFailed(null);
            }
        });
        try {
            pipeline.start();
        } catch(IOException e) {
            handler.onFailed(e);
            return;
        }
    }
}

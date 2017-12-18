package com.recoded.taqadam.models.db;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.recoded.taqadam.models.User;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by wisam on Dec 13 17.
 */

public class UserDbHandler {
    private static final String TAG = UserDbHandler.class.getSimpleName();
    private static final String EXCEPTION_MSG = "Used not signed in";
    public static final String
            FIRST_NAME = "first_n",
            LAST_NAME = "last_n",
            DISPLAY_NAME = "display_n",
            EMAIL_ADDRESS = "email_addr",
            EMAIL_CONFIRMED = "is_email_confirmed",
            PHONE_NUMBER = "phone_number",
            PHONE_CONFIRMED = "is_phone_confirmed",
            USER_ADDRESS = "user_address",
            USER_DOB_TS = "dob_ts",
            USER_GENDER = "gender",
            DISPLAY_IMAGE = "display_img",
            GOVT_ID = "govt_id_img",
            IS_APPROVED = "is_approved", //IS THE USER ACCOUNT APPROVED BY TAQADAM, THIS SHOULD ALWAYS BE SET TO 0 BY THE APP AND TAQADAM'S ADMIN WILL CHANGE IT TO (TRUE)
            LAST_ACTIVE_TS = "last_active_ts",
            REG_ON_TS = "registered_on_ts";

    //TODO-wisam: MOVE THIS TO ITS OWN CLASS
    private static final String
            IS_ACTIVE = "is_active", //TAQADAM'S ADMIN CAN SUSPEND THE WORKERS WALLET SO NO TRANSACTION IS ALLOWED
            MONEY_AMOUNT = "money_amount";

    private static UserDbHandler handler;

    private DatabaseReference mDbReference;

    public static UserDbHandler getInstance() {
        if (handler == null) {
            handler = new UserDbHandler();
        }
        return handler;
    }

    private UserDbHandler() {
        String mUid = FirebaseAuth.getInstance().getUid();
        this.mDbReference = FirebaseDatabase.getInstance().getReference().child("Users").child(mUid);
    }

    public Task<Void> writeNewUser(User user) {
        Map<String, Object> keyValues = new HashMap<>();
        keyValues.put(FIRST_NAME, user.getFirstName());
        keyValues.put(LAST_NAME, user.getLastName());
        keyValues.put(DISPLAY_NAME, user.getDisplayName());
        keyValues.put(EMAIL_ADDRESS, user.getEmailAddress());
        keyValues.put(EMAIL_CONFIRMED, false);
        keyValues.put(PHONE_NUMBER, user.getPhoneNumber());
        keyValues.put(PHONE_CONFIRMED, false);
        keyValues.put(USER_ADDRESS, user.getUserAddress());
        keyValues.put(USER_DOB_TS, user.getDateOfBirth().getTime());
        keyValues.put(USER_GENDER, user.getGender().getGenderName());
        keyValues.put(DISPLAY_IMAGE, user.getPicturePath().toString());
        keyValues.put(GOVT_ID, "null");
        keyValues.put(IS_APPROVED, false);
        keyValues.put(REG_ON_TS, System.currentTimeMillis());

        return mDbReference.setValue(keyValues);
    }

    public Task<Void> updateUser(User user) {
        Map<String, Object> keyValues = new HashMap<>();
        keyValues.put(FIRST_NAME, user.getFirstName());
        keyValues.put(LAST_NAME, user.getLastName());
        keyValues.put(DISPLAY_NAME, user.getDisplayName());
        keyValues.put(EMAIL_ADDRESS, user.getEmailAddress());
        keyValues.put(PHONE_NUMBER, user.getPhoneNumber());
        keyValues.put(USER_ADDRESS, user.getUserAddress());
        keyValues.put(USER_DOB_TS, user.getDateOfBirth().getTime());
        keyValues.put(USER_GENDER, user.getGender().getGenderName());
        keyValues.put(DISPLAY_IMAGE, user.getPicturePath().toString());
        return mDbReference.updateChildren(keyValues);
    }

    public Task<DataSnapshot> fetchUserNode() {
        final TaskCompletionSource<DataSnapshot> source = new TaskCompletionSource<>();
        mDbReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                source.setResult(dataSnapshot);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                source.setException(databaseError.toException());
            }
        });

        return source.getTask();
    }
}

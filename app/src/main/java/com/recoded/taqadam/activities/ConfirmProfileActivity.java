package com.recoded.taqadam.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import androidx.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;
import androidx.exifinterface.media.ExifInterface;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.recoded.taqadam.R;
import com.recoded.taqadam.databinding.ActivityConfirmProfileBinding;
import com.recoded.taqadam.models.Api.Api;
import com.recoded.taqadam.models.Api.ApiError;
import com.recoded.taqadam.models.Api.InvalidException;
import com.recoded.taqadam.models.Error;
import com.recoded.taqadam.models.Profile;
import com.recoded.taqadam.objects.User;
import com.recoded.taqadam.models.auth.UserAuthHandler;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConfirmProfileActivity extends BaseActivity {
    private static final String TAG = ConfirmProfileActivity.class.getSimpleName();
    private static final int ACTIVITY_REQUEST_CODE_FILES = 1990;
    private static final int ACTIVITY_REQUEST_CODE_CAMERA = 1991;
    private static final int ACTIVITY_REQUEST_CODE_GALLERY = 1992;
    private static final int PERMISSION_REQUEST_CODE_STORAGE_READ = 2990;
    //private static final String EMAIL_REGEX = "^[a-zA-Z]+[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.{1}[a-zA-Z0-9-.]{2,}(?<!\\.)$";


    private ActivityConfirmProfileBinding binding;
    private Profile profile;
    private OnCompleteListener<User> mUserCreatedListener;
    private Calendar mCalendar;
    private DatePickerDialog.OnDateSetListener mDatePickedListener;
    private boolean isAnimating = false; // a work around for image animation

    private BottomSheetBehavior mIntentHandlerChooser;
    //Changing picture intents (camera, gallery, file explorer)
    private Intent cameraIntent, galleryIntent, filesIntent;
    private Uri cameraOutputFile; //Because there is no access to it;

    private ProgressDialog mCreatingAccountProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAuthorized();

        binding = DataBindingUtil.setContentView(this, R.layout.activity_confirm_profile);

        boolean mEditMode = getIntent().getBooleanExtra("EDIT_MODE", false);

        if (mEditMode) {
            binding.tvReady.setText(R.string.edit_profile);
            binding.tvAlmostThere.setVisibility(View.GONE);
        }
        binding.bSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateAndPush();
            }
        });

        mCalendar = Calendar.getInstance();

        mDatePickedListener = new DatePickerDialog.OnDateSetListener() {

            @Override
            public void onDateSet(DatePicker view, int year, int monthOfYear,
                                  int dayOfMonth) {
                mCalendar.set(Calendar.YEAR, year);
                mCalendar.set(Calendar.MONTH, monthOfYear);
                mCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                updateDobField();
            }
        };

        binding.etDob.getEditText().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new DatePickerDialog(ConfirmProfileActivity.this,
                        mDatePickedListener,
                        mCalendar.get(Calendar.YEAR),
                        mCalendar.get(Calendar.MONTH),
                        mCalendar.get(Calendar.DAY_OF_MONTH))
                        .show();
            }
        });

        binding.spinnerCities.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != 0) {
                    binding.tvSpinnerCitiesError.setText("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mIntentHandlerChooser = BottomSheetBehavior.from(binding.intentsChooserContainer);
        mIntentHandlerChooser.setHideable(true);
        mIntentHandlerChooser.setState(BottomSheetBehavior.STATE_HIDDEN);
        setupPictureIntents();
        binding.bChangePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
                    if (mIntentHandlerChooser.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                        mIntentHandlerChooser.setState(BottomSheetBehavior.STATE_EXPANDED);
                    } else {
                        mIntentHandlerChooser.setState(BottomSheetBehavior.STATE_HIDDEN);
                    }
                } else {
                    requestStoragePermission();
                }
            }
        });
        createProfileUpdatedListener();
        setProfile();

        mCreatingAccountProgressDialog = new ProgressDialog(this);
        mCreatingAccountProgressDialog.setCancelable(false);
        mCreatingAccountProgressDialog.setTitle(getString(R.string.updating_account));
        mCreatingAccountProgressDialog.setCanceledOnTouchOutside(false);
        mCreatingAccountProgressDialog.setMessage(getString(R.string.please_wait));
    }

    private void requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)) {
            // Provide an additional rationale to the user if the permission was not granted
            // and the user would benefit from additional context for the use of the permission.
            // Display a SnackBar with a button to request the missing permission.
            Snackbar.make(binding.getRoot(), R.string.storage_permission,
                    Snackbar.LENGTH_INDEFINITE).setAction(R.string.ok, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Request the permission
                    ActivityCompat.requestPermissions(ConfirmProfileActivity.this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            PERMISSION_REQUEST_CODE_STORAGE_READ);
                }
            }).show();

        } else {
            // Request the permission. The result will be received in onRequestPermissionResult().
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE_STORAGE_READ);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE_STORAGE_READ) {
            // Request for camera permission.
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission has been granted. Show intent chooser
                mIntentHandlerChooser.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else {
            }
        }
    }

    private void checkAuthorized() {
        if (UserAuthHandler.getInstance() == null) {
            final ProgressDialog pd = new ProgressDialog(this);
            pd.setTitle(R.string.Signingin);
            pd.setMessage(getString(R.string.Please_wait));
            pd.setCancelable(false);
            pd.setCanceledOnTouchOutside(false);
            pd.show();

            UserAuthHandler.init(this);
            UserAuthHandler.getInstance().getInitTask().addOnSuccessListener(this, new OnSuccessListener<User>() {
                @Override
                public void onSuccess(User user) {
                    pd.dismiss();
                    if (user == null) {
                        Intent i = new Intent(ConfirmProfileActivity.this, SigninActivity.class);
                        startActivity(i);
                        finish();
                    } else {
                        profile = user.getProfile();
                    }
                }
            }).addOnFailureListener(this, new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(ConfirmProfileActivity.this, "Error occured!", Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        } else if (UserAuthHandler.getInstance().shouldLogin()) {
            profile = new Profile();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.error);
            builder.setIcon(R.drawable.ic_error_black);
            builder.setMessage(R.string.not_logged_in);
            builder.setPositiveButton(R.string.login, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent(ConfirmProfileActivity.this, SigninActivity.class));
                    finish();
                }
            });
            builder.setNegativeButton(R.string.register, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent(ConfirmProfileActivity.this, RegisterActivity.class));
                    finish();
                }
            });
            builder.setCancelable(false);
            builder.create().show();
        } else {
            profile = UserAuthHandler.getInstance().getCurrentUser().getProfile();
        }
    }

    private void setProfile() {
        if (profile == null) return;
        binding.setProfile(profile);
        if (profile.getGenderEnum() == Profile.Gender.MALE) {
            binding.spinnerGender.setSelection(1);
        } else if (profile.getGenderEnum() == Profile.Gender.FEMALE) {
            binding.spinnerGender.setSelection(2);
        }

        if (profile.getAddress() != null) {
            binding.spinnerCities.setSelection(profile.getAddressId());
        }

        binding.ivDisplayImage.setTag(profile.getAvatar());
        loadImage();
        if (profile.getBirthDateObject() != null) {
            mCalendar.setTime(profile.getBirthDateObject());
            updateDobField();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (mIntentHandlerChooser.getState() == BottomSheetBehavior.STATE_EXPANDED) {

                Rect outRect = new Rect();
                binding.intentsChooserContainer.getGlobalVisibleRect(outRect);

                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY()))
                    mIntentHandlerChooser.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void updateDobField() {
        String format = "yyyy-MM-dd";
        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
        binding.etDob.setError("");
        binding.etDob.getEditText().setText(sdf.format(mCalendar.getTime()));
    }

    private void validateAndPush() {
        final String EMPTY = getString(R.string.this_field_is_required);
        final String INVALID = getString(R.string.invalid_input);
        final String INVALID_AGE = getString(R.string.age_less_than13);
        final String INVALID_CITY = getString(R.string.select_valid_city);

        boolean valid = true;

        //First Name Validator
        if (binding.etFName.getEditText().getText().toString().isEmpty()) {
            binding.etFName.getEditText().setError(EMPTY);
            valid = false;
        } else if (binding.etFName.getEditText().getText().toString().length() < 2) {
            binding.etFName.getEditText().setError(INVALID);
            valid = false;
        }

        //Last Name Validator
        if (binding.etLName.getEditText().getText().toString().isEmpty()) {
            binding.etLName.getEditText().setError(EMPTY);
            valid = false;
        } else if (binding.etLName.getEditText().getText().toString().length() < 2) {
            binding.etLName.getEditText().setError(INVALID);
            valid = false;
        }

        //Phone Number Validator
        if (binding.etPhoneNumber.getEditText().getText().toString().isEmpty()) {
            binding.etPhoneNumber.getEditText().setError(EMPTY);
            valid = false;
        } else if (binding.etPhoneNumber.getEditText().getText().toString().length() < 5) {
            binding.etPhoneNumber.getEditText().setError(INVALID);
            valid = false;
        }

        //DOB Validator
        if (binding.etDob.getEditText().getText().toString().isEmpty()) {
            binding.etDob.setError(EMPTY);
            valid = false;
        } else if (isYoung()) {
            binding.etDob.setError(INVALID_AGE);
            valid = false;
        } else {
            binding.etDob.setError(""); //We need to clear only this field because set to the layout
        }

        //City Validator
        if (binding.spinnerCities.getSelectedItemPosition() == 0) {
            ((TextView) binding.spinnerCities.getSelectedView()).setError(INVALID_CITY);
            binding.tvSpinnerCitiesError.setText(INVALID_CITY);
            valid = false;
        } else {
            binding.tvSpinnerCitiesError.setText(""); //We need to clear only this field because set to the layout
        }

        //ImageOld Validator
        if (binding.ivDisplayImage.getTag() == null) {
            if (!isAnimating) {
                isAnimating = true;
                binding.ivDisplayImage.animate().yBy(-50).setInterpolator(new DecelerateInterpolator()).setDuration(200).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        binding.ivDisplayImage.animate().yBy(50).setInterpolator(new BounceInterpolator()).setDuration(400).withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                isAnimating = false;
                            }
                        }).start();
                    }
                }).start();
            }
            valid = false;
        }

        //Push Data
        if (valid) {
            postProfile();
        }
    }

    private boolean isYoung() {
        Date today = Calendar.getInstance().getTime();
        final double acceptableDiff = 13 * 365.25d * 24 * 3600 * 1000; //13 years * 365 days * 24 hrs * 3600 secs * 1000 ms
        long diff = today.getTime() - mCalendar.getTimeInMillis();

        return diff < acceptableDiff;
    }

    private void createProfileUpdatedListener() {
        mUserCreatedListener = new OnCompleteListener<User>() {

            @Override
            public void onComplete(@NonNull Task<User> task) {
                if (task.isSuccessful()) {
                    mCreatingAccountProgressDialog.dismiss();
                    User user = task.getResult();
                    if (user.getProfile() != null) {
                        UserAuthHandler.getInstance().updateUser(user);
                        Toast.makeText(ConfirmProfileActivity.this, R.string.profile_updated, Toast.LENGTH_LONG).show();
                    }
                    Intent i = new Intent(ConfirmProfileActivity.this, MainActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(i);
                    finish();
                } else {
                    mCreatingAccountProgressDialog.dismiss();
                    ApiError error = (ApiError) task.getException();
                    if (error instanceof InvalidException)
                        indicateErrors((InvalidException) error);
                    else
                        Toast.makeText(ConfirmProfileActivity.this, R.string.error_updating_account, Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    private void indicateErrors(InvalidException error) {
        Map<String, List<Error>> errors = error.getErrors();
        for (String field : errors.keySet()) {
            String msg = errors.get(field).get(0).getMessage();
            switch (field) {
                case "first_name":
                    binding.etFName.getEditText().setError(msg);
                    break;
                case "last_name":
                    binding.etLName.getEditText().setError(msg);
                    break;
                case "birth_date":
                    binding.etDob.setError(msg);
                    break;
                case "phone":
                    binding.etPhoneNumber.getEditText().setError(msg);
                    break;
                case "address":
                    binding.tvSpinnerCitiesError.setText(msg);
                    break;
                default:
                    Toast.makeText(this, field + ": " + msg, Toast.LENGTH_LONG).show();
            }
        }
    }


    private void loadImage() {
        if (binding.ivDisplayImage.getTag() != null) {
            final Uri uri = (Uri) binding.ivDisplayImage.getTag();
            binding.pbDisplayImage.setVisibility(View.VISIBLE);
            Picasso.with(this)
                    .load(uri)
                    .placeholder(getResources().getDrawable(R.drawable.no_image))
                    .into(binding.ivDisplayImage, new Callback() {
                        @Override
                        public void onSuccess() {
                            binding.pbDisplayImage.setVisibility(View.GONE);
                            binding.ivDisplayImage.setBorderColor(getResources().getColor(R.color.colorPwStrong));
                        }

                        @Override
                        public void onError() {
                            binding.pbDisplayImage.setVisibility(View.GONE);
                            Log.d(TAG, "Picasso couldn't load the image: " + uri);
                        }
                    });
        }
    }

    private void setupPictureIntents() {
        cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        filesIntent = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
        PackageManager pm = getPackageManager();
        final ComponentName cameraComponent = cameraIntent.resolveActivity(pm);
        ComponentName galleryComponent = galleryIntent.resolveActivity(pm);
        ComponentName filesComponent = filesIntent.resolveActivity(pm);

        //Instantiate camera intent
        if (cameraComponent == null) {
            binding.intentCamera.setVisibility(View.GONE);
        }

        //Instantiate gallery intent
        if (galleryComponent != null) {
            galleryIntent.setType("image/*");
            galleryIntent.putExtra(MediaStore.MEDIA_IGNORE_FILENAME, ".nomedia");
        } else {
            binding.intentGallery.setVisibility(View.GONE);
        }

        //Set the layout for files intent
        if (filesComponent == null) {
            binding.intentFiles.setVisibility(View.GONE);
        }

        binding.intentFiles.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(filesIntent, ACTIVITY_REQUEST_CODE_FILES);
                mIntentHandlerChooser.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });

        binding.intentCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Create the file to save the captured image
                File photoFile = createImageFile();

                // Continue only if the File was successfully created
                if (photoFile != null) {
                    //Put Extras
                    cameraOutputFile = FileProvider.getUriForFile(ConfirmProfileActivity.this,
                            getPackageName().concat(".fileprovider"),
                            photoFile);
                    grantUriPermission(cameraComponent.getPackageName(), cameraOutputFile, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputFile);
                }

                startActivityForResult(cameraIntent, ACTIVITY_REQUEST_CODE_CAMERA);
                mIntentHandlerChooser.setState(BottomSheetBehavior.STATE_HIDDEN);

            }
        });

        binding.intentGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(galleryIntent, ACTIVITY_REQUEST_CODE_GALLERY);
                mIntentHandlerChooser.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });
    }

    private File createImageFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "Img_" + timeStamp;
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = null;
        try {
            image = File.createTempFile(
                    fileName,  /* prefix */
                    ".jpg",         /* suffix */
                    storageDir      /* directory */
            );
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (image != null) {
                image.deleteOnExit();
            }
        }

        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case ACTIVITY_REQUEST_CODE_CAMERA:
                    if (cameraOutputFile != null) { //Just to make sure file was created successfully
                        Uri uri = cameraOutputFile;
                        Log.d(TAG, "from camera: " + uri);
                        binding.ivDisplayImage.setImageDrawable(getResources().getDrawable(R.drawable.no_image));
                        binding.pbDisplayImage.setVisibility(View.VISIBLE);
                        processAndUploadImageToStorage(uri);
                    } else {
                        Log.d(TAG, "ImageOld file is null");
                    }
                    break;

                case ACTIVITY_REQUEST_CODE_GALLERY:
                case ACTIVITY_REQUEST_CODE_FILES:
                    Uri uri = imageReturnedIntent.getData();
                    Log.d(TAG, "from gallery: " + uri);
                    binding.ivDisplayImage.setImageDrawable(getResources().getDrawable(R.drawable.no_image));
                    binding.pbDisplayImage.setVisibility(View.VISIBLE);
                    processAndUploadImageToStorage(uri);
                    break;
            }
        }
    }

    private void processAndUploadImageToStorage(final Uri uri) {
        ImageProcessor ip = new ImageProcessor();
        String p = getPath(uri);
        Log.d(TAG, "image path: " + p);
        ip.execute(p);
        ip.processTask.addOnSuccessListener(this, new OnSuccessListener<Bitmap>() {
            @Override
            public void onSuccess(Bitmap bmp) {
                uploadImage(bmp);
            }
        });
    }

    private String getPath(Uri uri) {

        //Files Provider
        if (uri.getScheme().equalsIgnoreCase("file")) {
            return uri.getPath();
        }

        //App Provider
        if (uri.getAuthority().contains(getPackageName())) {
            String fn = "/".concat(uri.getLastPathSegment());
            String dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES).getPath();
            return dir + fn;
        }

        //Try File Path
        try {
            String p = FilePath.getPath(this, uri);
            if (p != null) return p;
        } catch (Exception ignored) {

        }

        //Handle other providers
        File tempFile = createImageFile();
        FileOutputStream os;
        InputStream is;
        try {
            os = new FileOutputStream(tempFile);
            is = getContentResolver().openInputStream(uri);
            byte[] buf = new byte[1024];
            int len;
            if (is != null) {
                while ((len = is.read(buf)) > 0) {
                    os.write(buf, 0, len);
                }
                is.close();
                os.close();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return tempFile.getPath();
    }

    private void uploadImage(Bitmap bmp) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos);
        byte[] bytes = bos.toByteArray();

        if (bytes.length > 500 * 1024) {
            Toast.makeText(this, "This image is too large. Try a smaller one", Toast.LENGTH_LONG).show();
            return;
        }
        FileOutputStream fos = null;
        File img = createImageFile();
        try {
            fos = new FileOutputStream(img);
            fos.write(bytes);
            fos.flush();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "Uploading bitmap display image");
        Api.uploadImage(img).addOnSuccessListener(this, new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                binding.ivDisplayImage.setTag(uri);
                //Todo: implement old and new in the future to allow user to revert
                //user.getProfile().setAvatar(task.getResult().toString());
                loadImage();
            }
        }).addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                String msg = "Error uploading image";
                if (e instanceof InvalidException) {
                    String extraMsg = ((InvalidException) e).getErrors().get("avatar") == null ? "" : ((InvalidException) e).getErrors().get("avatar").get(0).getMessage();
                    msg += extraMsg;
                }
                Toast.makeText(ConfirmProfileActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void postProfile() {
        Profile p = new Profile();
        p.setFirstName(binding.etFName.getEditText().getText().toString());
        p.setLastName(binding.etLName.getEditText().getText().toString());
        p.setAvatar(binding.ivDisplayImage.getTag().toString());
        p.setBirthDate(binding.etDob.getEditText().getText().toString());
        p.setPhone(binding.etPhoneNumber.getEditText().getText().toString());
        p.setGender(binding.spinnerGender.getSelectedItem().toString());

        int pos = binding.spinnerCities.getSelectedItemPosition();
        for (Profile.City c : Profile.City.values()) {
            if (c.getId() == pos)
                p.setAddress(c.getName());
        }

        if (profile == null) {
            Log.d(TAG, "Posting profile");
            mCreatingAccountProgressDialog.show();
            Api.updateProfile(p, false).addOnCompleteListener(this, this.mUserCreatedListener);
        } else {
            Log.d(TAG, "Putting profile");
            mCreatingAccountProgressDialog.show();
            Api.updateProfile(p, true).addOnCompleteListener(this, this.mUserCreatedListener);
        }
    }

    private static class ImageProcessor extends AsyncTask<String, Void, Void> {
        final TaskCompletionSource<Bitmap> src = new TaskCompletionSource<>();
        final Task<Bitmap> processTask = src.getTask();

        @Override
        protected Void doInBackground(String... path) {
            Bitmap bmp = BitmapFactory.decodeFile(path[0]);
            if (bmp == null) {
                src.setException(new IOException());
                return null;
            }
            float rotation = getRequiredRotation(path[0]);
            float scale = 1000f / Math.max(bmp.getWidth(), bmp.getHeight());
            Matrix matrix = new Matrix();

            if (rotation != 0)
                matrix.postRotate(rotation);

            if (scale < 1)
                matrix.postScale(scale, scale);

            if (!matrix.isIdentity())
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(),
                        matrix, true);

            src.setResult(bmp);
            return null;
        }

        private float getRequiredRotation(String path) {
            float rotation = 0;
            try {
                ExifInterface ei = new ExifInterface(path);
                int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);

                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        rotation = 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        rotation = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        rotation = 270;
                        break;
                }
            } catch (IOException e) {
                Log.w("ImageProcessor", "Error opening image file: " + path);
                e.printStackTrace();
            }

            return rotation;
        }

    }

    public static class FilePath {

        /**
         * Method for return file path of Gallery image/ Document / Video / Audio
         *
         * @param context
         * @param uri
         * @return path of the selected image file from gallery
         */
        @SuppressLint("NewApi")
        public static String getPath(final Context context, final Uri uri) {

            // check here to KITKAT or new version
            final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

            // DocumentProvider
            if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {

                // ExternalStorageProvider
                if (isExternalStorageDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];

                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory() + "/"
                                + split[1];
                    }
                }
                // DownloadsProvider
                else if (isDownloadsDocument(uri)) {

                    final String id = DocumentsContract.getDocumentId(uri);
                    final Uri contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"),
                            Long.valueOf(id));

                    return getDataColumn(context, contentUri, null, null);
                }
                // MediaProvider
                else if (isMediaDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];

                    Uri contentUri = null;
                    if ("image".equals(type)) {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    } else if ("video".equals(type)) {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    } else if ("audio".equals(type)) {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    }

                    final String selection = "_id=?";
                    final String[] selectionArgs = new String[]{split[1]};

                    return getDataColumn(context, contentUri, selection,
                            selectionArgs);
                }
            }
            // MediaStore (and general)
            else if ("content".equalsIgnoreCase(uri.getScheme())) {

                // Return the remote address
                if (isGooglePhotosUri(uri))
                    return uri.getLastPathSegment();

                return getDataColumn(context, uri, null, null);
            }
            // File
            else if ("file".equalsIgnoreCase(uri.getScheme())) {
                return uri.getPath();
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
        public static String getDataColumn(Context context, Uri uri,
                                           String selection, String[] selectionArgs) {

            Cursor cursor = null;
            final String column = "_data";
            final String[] projection = {column};

            try {
                cursor = context.getContentResolver().query(uri, projection,
                        selection, selectionArgs, null);
                if (cursor != null && cursor.moveToFirst()) {
                    final int index = cursor.getColumnIndexOrThrow(column);
                    return cursor.getString(index);
                }
            } finally {
                if (cursor != null)
                    cursor.close();
            }
            return null;
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is ExternalStorageProvider.
         */
        public static boolean isExternalStorageDocument(Uri uri) {
            return "com.android.externalstorage.documents".equals(uri
                    .getAuthority());
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is DownloadsProvider.
         */
        public static boolean isDownloadsDocument(Uri uri) {
            return "com.android.providers.downloads.documents".equals(uri
                    .getAuthority());
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is MediaProvider.
         */
        public static boolean isMediaDocument(Uri uri) {
            return "com.android.providers.media.documents".equals(uri
                    .getAuthority());
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is Google Photos.
         */
        public static boolean isGooglePhotosUri(Uri uri) {
            return "com.google.android.apps.photos.content".equals(uri
                    .getAuthority());
        }
    }
}


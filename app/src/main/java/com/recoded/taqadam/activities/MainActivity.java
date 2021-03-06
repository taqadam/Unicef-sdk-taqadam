package com.recoded.taqadam.activities;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.recoded.taqadam.views.LockableViewPager;
import com.recoded.taqadam.adapters.MainPagerAdapter;
import com.recoded.taqadam.R;
import com.recoded.taqadam.models.Responses.SuccessResponse;
import com.recoded.taqadam.objects.User;
import com.recoded.taqadam.models.auth.UserAuthHandler;
import com.squareup.picasso.Picasso;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener {

    private User user;
    private UserAuthHandler mAuth;
    private View drawerHeader;
    private LockableViewPager pager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if ((mAuth = UserAuthHandler.getInstance()) == null) {
            final ProgressDialog pd = new ProgressDialog(this);
            pd.setTitle(R.string.Signingin);
            pd.setMessage(getString(R.string.Please_wait));
            pd.setCancelable(false);
            pd.setCanceledOnTouchOutside(false);
            pd.show();

            UserAuthHandler.init(this);
            mAuth = UserAuthHandler.getInstance();
            mAuth.getInitTask().addOnSuccessListener(this, new OnSuccessListener<User>() {
                @Override
                public void onSuccess(User user) {
                    pd.dismiss();
                    if (user == null) {
                        Intent i = new Intent(MainActivity.this, SigninActivity.class);
                        startActivity(i);
                        finish();
                    } else {
                        MainActivity.this.user = user;
                        startActivity();
                    }
                }
            }).addOnFailureListener(this, new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(MainActivity.this, "Error occured!", Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        } else {
            this.user = mAuth.getCurrentUser();
            startActivity();
        }
    }

    private void startActivity() {
        checkAuthorized();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setupBottomNavigation();

        setupNavigationDrawer(toolbar);
    }

    private void setupNavigationDrawer(Toolbar toolbar) {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        drawerHeader = navigationView.inflateHeaderView(R.layout.activity_main_drawer);
        initHeader();
    }

    private void setupBottomNavigation() {
        pager = findViewById(R.id.main_pager);
        pager.setLocked(true);
        pager.setAdapter(new MainPagerAdapter(getSupportFragmentManager()));

        BottomNavigationView.OnNavigationItemSelectedListener listener = new BottomNavigationView.OnNavigationItemSelectedListener() {

            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.navigation_assignments:
                        setTitle(item.getTitle());
                        pager.setCurrentItem(0);

                        return true;
                    case R.id.navigation_qa:
                        setTitle(item.getTitle());
                        pager.setCurrentItem(1);

                        return true;
                    case R.id.navigation_cash_out:
                        setTitle(item.getTitle());
                        pager.setCurrentItem(2);

                        return true;
                }
                return false;
            }
        };

        BottomNavigationView navigation = findViewById(R.id.bottom_navigation);
        navigation.setOnNavigationItemSelectedListener(listener);
        navigation.setSelectedItemId(0);
    }

    /*@Override
    protected void onStart() {
        super.onStart();
        initHeader() // moved to setupNavigationDrawer to fix the bug related to null headerView when not signed in.
    }*/

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
        } else if (id == R.id.nav_notification) {


        } else if (id == R.id.nav_setting) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_help) {
            startActivity(new Intent(this, HelpActivity.class));
        } else if (id == R.id.nav_feedback) {
            startActivity(new Intent(this, FeedbackActivity.class));
        } else if (id == R.id.nav_about) {
            startActivity(new Intent(this, AboutActivity.class));
        } else if (id == R.id.nav_logout) {
            UserAuthHandler.getInstance().logout().addOnSuccessListener(this, new OnSuccessListener<SuccessResponse>() {
                @Override
                public void onSuccess(SuccessResponse successResponse) {
                    if (successResponse != null) {
                        Toast.makeText(MainActivity.this, successResponse.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    startActivity(new Intent(MainActivity.this, SigninActivity.class));
                    finish();
                }
            });
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void checkAuthorized() {
        if (user == null) {
            user = new User();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.error);
            builder.setIcon(R.drawable.ic_error_black);
            builder.setMessage(R.string.not_logged_in);
            builder.setPositiveButton(R.string.login, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent(MainActivity.this, SigninActivity.class));
                    finish();
                }
            });
            builder.setNegativeButton(R.string.register, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent(MainActivity.this, RegisterActivity.class));
                    finish();
                }
            });
            builder.setCancelable(false);
            builder.create().show();
        }
    }

    private void initHeader() {
        TextView tv = drawerHeader.findViewById(R.id.tv_display_name);
        tv.setText(user.getName());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
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
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_discussion) {
            startActivity(new Intent(this, PostsFeedActivity.class));
            return true;
        } else if (id == R.id.action_notification) {
            return true;
        } else if (id == android.R.id.home) {
            onBackPressed();
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        /*JobDbHandler.getInstance().release();
        ImageDbHandler.getInstance().release();
        PostDbHandler.getInstance().release();*/
        super.onDestroy();
    }
}



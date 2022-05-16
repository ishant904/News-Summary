package com.example.finalnews

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.ViewPager
import com.example.finalnews.Adapters.SectionsPageAdapter
import com.example.finalnews.Fragments.CategoriesSlideFragment
import com.example.finalnews.Fragments.CountriesSlideFragment
import com.example.finalnews.Fragments.PublishersSlideFragment
import com.example.finalnews.Sources.SourcesInfo
import com.example.finalnews.viewModelsAndFactory.SelectionViewModel
import com.example.finalnews.viewModelsAndFactory.SelectionViewModelFactory
import com.example.finalnews.databinding.ActivityMainBinding
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.ads.MobileAds
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity(),PublishersSlideFragment.OnItemClickListener,CategoriesSlideFragment.OnItemClickListener, CountriesSlideFragment.OnItemClickListener,SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var viewModelFactory: SelectionViewModelFactory
    private lateinit var selectionViewModel: SelectionViewModel
    private lateinit var pref: SharedPreferences
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var binding: ActivityMainBinding

    companion object{
        private const val RC_SIGN_IN = 1
        const val SOURCES = "sources"
        var TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        pref = PreferenceManager.getDefaultSharedPreferences(this)
        pref.registerOnSharedPreferenceChangeListener(this)
        val themeName = pref.getString(resources.getString(R.string.theme_key), resources.getString(R.string.theme_default))
        if(themeName?.equals(resources.getString(R.string.lightLabel)) == true){
            setTheme(R.style.LightTheme)
        } else if (themeName?.equals(resources.getString(R.string.darkLabel)) == true) {
            setTheme(R.style.DarkTheme)
        }
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        setSupportActionBar(binding.toolbar)
        Log.d(TAG,"oncreate")
        //AdMob - ca-app-pub-3940256099942544~3347511713 is a sample ID for testing. In production use the actual app ID
        MobileAds.initialize(this){}
        //AdMob "ca-app-pub-3940256099942544/1033173712" is a test ad unit ID. In production use the add ad unit ID.

        viewModelFactory = SelectionViewModelFactory(application)
        selectionViewModel = ViewModelProvider(this,viewModelFactory).get(SelectionViewModel::class.java)
        binding.viewModel = selectionViewModel
        mFirebaseAuth = Firebase.auth

        selectionViewModel.mFirebaseUser.observe(this) {
            if (it == null) {
                val googleIdp: AuthUI.IdpConfig = AuthUI.IdpConfig.GoogleBuilder().build()
                val facebookIdp: AuthUI.IdpConfig = AuthUI.IdpConfig.FacebookBuilder().build()
                val emailIdp: AuthUI.IdpConfig = AuthUI.IdpConfig.EmailBuilder().build()
                startActivityForResult(
                    AuthUI.getInstance().createSignInIntentBuilder()
                        .setTheme(R.style.FirebaseLoginTheme).setIsSmartLockEnabled(false)
                        .setAvailableProviders(
                            listOf(googleIdp, facebookIdp, emailIdp)
                        ).setLogo(R.drawable.communication).setTosAndPrivacyPolicyUrls(
                        application.resources.getString(
                            R.string.terms_of_service
                        ), application.resources.getString(R.string.privacy_policy)
                    ).build(), RC_SIGN_IN
                )
            }
        }

        selectionViewModel.loadUI.observe(this) {
            if (it) {
                updateUI()
                updateNoOfItemsSelected()
            }
        }

        selectionViewModel.newsArticlesNavigate.observe(this) {
            if (it) {
                val newsArticlesActivity = Intent(applicationContext, NewsArticles::class.java)
                val mSourceInfo = SourcesInfo(
                    selectionViewModel.mPublishersSelectedLv.value,
                    selectionViewModel.mCategoriesSelectedLv.value,
                    selectionViewModel.mCountriesSelectedLv.value
                )
                newsArticlesActivity.putExtra(SOURCES, mSourceInfo)
                startActivity(newsArticlesActivity)
                selectionViewModel.onNewsArticlesNavigationComplete()
            }
        }

        selectionViewModel.snackbarMsg.observe(this) {
            if (it.isNotEmpty()) {
                showSnackbar(it)
                selectionViewModel.onSnackBarShown()
            }
        }

        selectionViewModel.showAD.observe(this) {
            if (it) {
                selectionViewModel.mInterstitialAd?.show(this)
                selectionViewModel.showInterstitialAdComplete()
                selectionViewModel.onUILoadComplete()
            }
        }

    }

    private fun updateUI(){
        setupViewPager(binding.sourcesContainer)
        binding.tabs.setupWithViewPager(binding.sourcesContainer)
        Log.d(TAG, "update ui")
    }

    private fun setupViewPager(viewPager: ViewPager){
        val adapter = SectionsPageAdapter(supportFragmentManager)
        val mPublishersFragment = PublishersSlideFragment()
        val mCategoriesFragment = CategoriesSlideFragment()
        val mCountriesFragment = CountriesSlideFragment()
        adapter.addFragment(mPublishersFragment, resources.getString(R.string.publishers_label))
        adapter.addFragment(mCountriesFragment, resources.getString(R.string.countries_label))
        adapter.addFragment(mCategoriesFragment, resources.getString(R.string.categories_label))
        viewPager.adapter = adapter
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == RC_SIGN_IN){
            if(resultCode == RESULT_OK){
                val message = "Welcome " + mFirebaseAuth.currentUser?.displayName + "!"
                showSnackbar(message)
            }else if (resultCode == RESULT_CANCELED){
                Toast.makeText(this, "Sign in cancelled!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_activity_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.sign_out_menu) {
            //mFirebaseAuth.signOut()
                AuthUI.getInstance().signOut(this)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        pref.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onResume() {
        super.onResume()
        pref.registerOnSharedPreferenceChangeListener(this)
    }

    private fun showSnackbar(message : String){
        val view = binding.mainLinearLayout
        val duration = Snackbar.LENGTH_SHORT
        Snackbar.make(view, message, duration).show()
    }

    override fun onItemClickPublishers(item: String) {
        selectionViewModel.onPublisherClicked(item)
        updateNoOfItemsSelected()
    }

    override fun onItemClickCategories(item: String) {
        selectionViewModel.onCategoryClicked(item)
        updateNoOfItemsSelected()
    }

    override fun onItemClickCountries(item: String) {
        selectionViewModel.onCountryClicked(item)
        updateNoOfItemsSelected()
    }

    private fun updateNoOfItemsSelected(){
        if(selectionViewModel.mPublishersSelectedLv.value != null || selectionViewModel.mCategoriesSelectedLv.value != null || selectionViewModel.mCountriesSelectedLv.value != null) {
            val numberSelected = (selectionViewModel.mPublishersSelectedLv.value?.size ?: 0 ) + (selectionViewModel.mCategoriesSelectedLv.value?.size ?: 0) + (selectionViewModel.mCountriesSelectedLv.value?.size ?: 0)
            val selectedLabel = "$numberSelected selected"
            binding.textViewNoItemsSelected.text = selectedLabel
        } else {
            binding.textViewNoItemsSelected.text = getString(R.string.selected_label)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(TAG,"onshared")
        if(key.equals(resources.getString(R.string.theme_key))){
            recreate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG,"destroy")
    }
}

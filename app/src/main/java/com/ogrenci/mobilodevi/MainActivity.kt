package com.ogrenci.mobilodevi

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.ogrenci.mobilodevi.ui.theme.MobilodeviTheme
import com.google.firebase.Firebase
import com.google.firebase.database.database
import java.util.Locale

enum class AppScreen { LOGIN, MSKU, TF, BSM }

class MainActivity : ComponentActivity() {

    companion object {
        const val OZEL_EYLEM = "com.ogrenci.mobilodevi.OZEL_MESAJ"
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val mesaj = intent.getStringExtra("mesaj")
            Toast.makeText(context, "Broadcast alındı: $mesaj", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MobilodeviTheme { MainNavigation() } }
    }

    fun broadcastIntent() {
        val intent = Intent(OZEL_EYLEM).apply {
            putExtra("mesaj", "Merhaba BSM3518!")
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(OZEL_EYLEM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }
}

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(AppScreen.LOGIN) }
    val dbHelper = remember { DatabaseHelper(context) }
    val firebaseDb = remember { Firebase.database.getReference("bsm3518") }
    var konumBilgisi by remember { mutableStateOf("Konum alınmadı") }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                client.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val adresler = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        konumBilgisi = adresler?.get(0)?.getAddressLine(0) ?: "Adres alınamadı"
                    }
                }
            }
        } else {
            Toast.makeText(context, "Konum izni verilmedi!", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> if (bitmap != null) Toast.makeText(context, "Fotoğraf çekildi!", Toast.LENGTH_SHORT).show() }

    fun firebaseKaydet(ad: String, soyad: String, numara: String) {
        val ogrenciMap = mapOf("ad" to ad, "soyad" to soyad, "numara" to numara)
        firebaseDb.push().setValue(ogrenciMap)
            .addOnSuccessListener { Toast.makeText(context, "Firebase: Kaydedildi!", Toast.LENGTH_SHORT).show() }
    }

    fun mesajUygulamasiniAc(paketAdi: String, mesaj: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(paketAdi)
            putExtra(Intent.EXTRA_TEXT, mesaj)
        }
        try { context.startActivity(intent) } catch (e: Exception) {
            Toast.makeText(context, "Uygulama yüklü değil!", Toast.LENGTH_SHORT).show()
        }
    }

    var ad by remember { mutableStateOf("") }
    var soyad by remember { mutableStateOf("") }
    var numara by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var showDerslerDialog by remember { mutableStateOf(false) }
    var secilenSinif by remember { mutableStateOf("") }

    val prefs = context.getSharedPreferences("ayarlar", Context.MODE_PRIVATE)
    prefs.edit().putInt("sinif", 3).apply()
    val sinif = prefs.getInt("sinif", 0)

    val dersler = mapOf(
        "1. Sınıf" to listOf("Matematik I", "Fizik I", "Programlamaya Giriş", "İngilizce I"),
        "2. Sınıf" to listOf("Veri Yapıları", "Matematik II", "Elektronik", "İngilizce II"),
        "3. Sınıf" to listOf("BSM3518 Mobil Programlama", "Yapay Zeka", "Veri Madenciliği", "Ağ Güvenliği"),
        "4. Sınıf" to listOf("Bitirme Projesi", "Staj", "Seçmeli Ders I", "Seçmeli Ders II")
    )

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            slideInHorizontally(animationSpec = tween(500)) togetherWith
                    slideOutHorizontally(animationSpec = tween(500))
        },
        label = ""
    ) { screen ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (screen) {
                AppScreen.LOGIN -> {
                    Text("Öğrenci Girişi", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    // (b) Konum
                    Button(
                        onClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Konumu Al") }
                    Text(konumBilgisi, modifier = Modifier.padding(8.dp))
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(value = ad, onValueChange = { ad = it }, label = { Text("Ad") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = soyad, onValueChange = { soyad = it }, label = { Text("Soyad") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = numara, onValueChange = { numara = it }, label = { Text("No (BSM3...)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        if (numara.startsWith("BSM3") && ad.isNotEmpty()) {
                            dbHelper.ogrenciEkle(ad, soyad, numara)
                            firebaseKaydet(ad, soyad, numara)
                            showDialog = true
                            currentScreen = AppScreen.MSKU
                        } else { Toast.makeText(context, "Hatalı Giriş!", Toast.LENGTH_SHORT).show() }
                    }) { Text("Onayla ve Kaydet") }
                }

                // (c) MSKÜ dizini
                AppScreen.MSKU -> {
                    Text("MSKÜ", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        Toast.makeText(context, "TF dizinine geçiliyor...", Toast.LENGTH_SHORT).show()
                        currentScreen = AppScreen.TF
                    }, modifier = Modifier.fillMaxWidth()) { Text("📁 TF - Teknoloji Fakültesi") }
                }

                // (c) TF dizini
                AppScreen.TF -> {
                    Text("Teknoloji Fakültesi", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        Toast.makeText(context, "BSM dizinine geçiliyor...", Toast.LENGTH_SHORT).show()
                        currentScreen = AppScreen.BSM
                    }, modifier = Modifier.fillMaxWidth()) { Text("📁 BSM - Bilgisayar Sistemleri") }
                }

                // (d) BSM dizini - sınıf dersleri
                AppScreen.BSM -> {
                    Text("BSM Bölümü", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("SharedPreferences'tan okunan sınıf: $sinif")
                    Spacer(modifier = Modifier.height(8.dp))

                    // (d) Sınıf dersleri
                    dersler.forEach { (sinifAdi, dersList) ->
                        Button(
                            onClick = {
                                secilenSinif = sinifAdi
                                showDerslerDialog = true
                                // (f) AlertDialog ile bildirim
                                Toast.makeText(context, "$sinifAdi seçildi", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) { Text("📚 $sinifAdi") }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // (j) SharedPreferences
                    Button(onClick = {
                        val okunan = prefs.getInt("sinif", 0)
                        Toast.makeText(context, "Sınıf: $okunan (getInt ile okundu)", Toast.LENGTH_LONG).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Sınıfı Göster (getInt)") }

                    // (h) Kamera
                    Button(onClick = { cameraLauncher.launch() }, modifier = Modifier.fillMaxWidth()) { Text("📷 Kamera") }

                    // (h) Mesajlaşma uygulamaları
                    Button(onClick = { mesajUygulamasiniAc("com.whatsapp", "BSM3518 ödev tamam!") }, modifier = Modifier.fillMaxWidth()) { Text("WhatsApp") }
                    Button(onClick = { mesajUygulamasiniAc("org.telegram.messenger", "BSM3518 ödev tamam!") }, modifier = Modifier.fillMaxWidth()) { Text("Telegram") }
                    Button(onClick = { mesajUygulamasiniAc("com.turkcell.bip", "BSM3518 ödev tamam!") }, modifier = Modifier.fillMaxWidth()) { Text("BiP") }

                    // (h) SMS
                    Button(onClick = {
                        try {
                            val smsManager = SmsManager.getDefault()
                            smsManager.sendTextMessage("+905000000000", null, "BSM3518 test SMS", null, null)
                            Toast.makeText(context, "SMS gönderildi!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "SMS gönderilemedi!", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("SMS Gönder") }

                    // (k) Broadcast
                    val activity = context as MainActivity
                    Button(onClick = { activity.broadcastIntent() }, modifier = Modifier.fillMaxWidth()) { Text("Broadcast Gönder") }
                }
            }
        }
    }

    // (e) Onay dialogu
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Onay") },
            text = { Text("$ad $soyad — BSM 3. Sınıf öğrencisi olduğunuz onaylandı!\nVeriler kaydedildi.") },
            confirmButton = { Button(onClick = { showDialog = false }) { Text("Tamam") } }
        )
    }

    // (d) Dersler dialogu
    if (showDerslerDialog) {
        AlertDialog(
            onDismissRequest = { showDerslerDialog = false },
            title = { Text(secilenSinif) },
            text = {
                Column {
                    dersler[secilenSinif]?.forEach { ders ->
                        Text("• $ders", modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            },
            confirmButton = { Button(onClick = { showDerslerDialog = false }) { Text("Kapat") } }
        )
    }
}

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "ogrenciler.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE bsm3518 (id INTEGER PRIMARY KEY AUTOINCREMENT, ad TEXT, soyad TEXT, numara TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun ogrenciEkle(ad: String, soyad: String, numara: String) {
        val db = this.writableDatabase
        val cv = ContentValues().apply {
            put("ad", ad)
            put("soyad", soyad)
            put("numara", numara)
        }
        db.insert("bsm3518", null, cv)
    }
}
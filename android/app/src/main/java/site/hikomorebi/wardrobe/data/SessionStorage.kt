package site.hikomorebi.wardrobe.data

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SessionStorage {
    var server: String

    fun read(): StoredSession?

    fun write(session: StoredSession?)
}

// Rotated refresh secrets must be durably saved before another request can use them.
@SuppressLint("ApplySharedPref", "UseKtx")
class SecureSessionStorage(context: Context) : SessionStorage {
    private val prefs = context.getSharedPreferences("wardrobe_private", Context.MODE_PRIVATE)
    private val alias = "wardrobe.session.aes"
    override var server: String
        get() = prefs.getString("server", "https://cd.hikomorebi.site/")!!
        set(value) {
            check(prefs.edit().putString("server", value).commit()) { "无法保存服务器设置" }
        }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }
            .generateKey()
    }

    override fun read(): StoredSession? {
        val encoded = prefs.getString("session", null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            apiJson.fromJson(
                String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8),
                StoredSession::class.java,
            )
        } catch (_: Exception) {
            // Includes a restored ciphertext whose non-exportable key belongs to another device.
            prefs.edit().remove("session").commit()
            null
        }
    }

    override fun write(session: StoredSession?) {
        val editor = prefs.edit()
        if (session == null) editor.remove("session")
        else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted =
                cipher.iv + cipher.doFinal(apiJson.toJson(session).toByteArray(Charsets.UTF_8))
            editor.putString("session", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }
        check(editor.commit()) { "无法安全保存登录状态，请检查手机存储空间" }
    }
}

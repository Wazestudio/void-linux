package com.voidlinux.feature.tor

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.voidlinux.core.designsystem.Components

/**
 * Activité de déverrouillage du navigateur.
 * L'utilisateur saisit un PIN ou dessine un schéma.
 */
class LockActivity : AppCompatActivity() {

    private lateinit var lock: BrowserLock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lock = BrowserLock(this)

        when (lock.lockType) {
            BrowserLock.LockType.PIN -> showPinDialog()
            BrowserLock.LockType.PATTERN -> showPatternDialog()
            BrowserLock.LockType.NONE -> {
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun showPinDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Entrez le PIN"
        }

        AlertDialog.Builder(this)
            .setTitle("Navigateur verrouillé")
            .setMessage("Entre ton code PIN pour accéder au navigateur")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Déverrouiller") { _, _ ->
                if (lock.checkPin(input.text.toString())) {
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Components.showSnack(findViewById(android.R.id.content), "PIN incorrect")
                    showPinDialog()
                }
            }
            .setNegativeButton("Annuler") { _, _ ->
                setResult(RESULT_CANCELED)
                finish()
            }
            .show()
    }

    private fun showPatternDialog() {
        // Interface de schéma simplifiée : l'utilisateur entre une séquence
        // Pour une vraie PatternView, utiliser androidx.security ou PatternLockView
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Ex: 1235789"
        }

        AlertDialog.Builder(this)
            .setTitle("Navigateur verrouillé")
            .setMessage("Entre ton schéma (séquence de chiffres) pour accéder au navigateur")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Déverrouiller") { _, _ ->
                if (lock.checkPattern(input.text.toString())) {
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this, "Schéma incorrect", Toast.LENGTH_SHORT).show()
                    showPatternDialog()
                }
            }
            .setNegativeButton("Annuler") { _, _ ->
                setResult(RESULT_CANCELED)
                finish()
            }
            .show()
    }
}
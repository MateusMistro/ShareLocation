package br.edu.puccampinas.sharelocation

import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import br.edu.puccampinas.sharelocation.databinding.ActivityLoginBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

private lateinit var binding: ActivityLoginBinding
private lateinit var auth: FirebaseAuth

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Verificar se o usuário está logado
        val currentUser = auth.currentUser
        if (currentUser != null) {
            startActivity(Intent(this, MapsActivity::class.java))
            finish()
            return
        }

        binding.etCriar.setOnClickListener {
            startActivity(Intent(this,CreateAccountActivity::class.java))
        }

        binding.btnEntrar.setOnClickListener {

            val email = binding.etEmail.text.toString()
            val senha = binding.etSenha.text.toString()
            if (email.isNullOrEmpty() || senha.isNullOrEmpty()) {
                when {
                    email.isEmpty() -> {
                        mensagemNegativa(binding.root, "Preencha seu email")
                    }

                    senha.isEmpty() -> {
                        mensagemNegativa(binding.root, "Preencha sua senha")
                    }

                    senha.length <= 5 -> {
                        mensagemNegativa(binding.root, "A senha precisa ter pelo menos seis caracteres")
                    }
                }

            } else {

                auth.signInWithEmailAndPassword(email, senha)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {

                            val user = auth.currentUser
                            val userId = user?.uid

                            val userDocRef = FirebaseFirestore.getInstance().collection("pessoa")
                                .document(userId!!)
                            userDocRef.get().addOnSuccessListener { documentSnapshot ->
                                if (documentSnapshot.exists()) {
                                    startActivity(Intent(this, MapsActivity::class.java))

                                }
                            }

                        } else {
                            // Se o login falhar, exibe uma mensagem de erro
                            Log.w(ContentValues.TAG, "signInWithEmail:failure", task.exception)
                            mensagemNegativa(binding.root, "Email ou senha inválidos, tente novamente")

                        }
                    }
            }


        }
    }

    private fun mensagemNegativa(view: View, mensagem: String) {
        val snackbar = Snackbar.make(view, mensagem, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(Color.parseColor("#F3787A"))
        snackbar.setTextColor(Color.parseColor("#FFFFFF"))
        snackbar.show()
    }

    private fun mensagemPositive(view: View, mensagem: String) {
        val snackbar = Snackbar.make(view, mensagem, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(Color.parseColor("#78F37A"))
        snackbar.setTextColor(Color.parseColor("#FFFFFF"))
        snackbar.show()
    }
}

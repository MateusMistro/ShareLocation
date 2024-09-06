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
import br.edu.puccampinas.sharelocation.databinding.ActivityCreateAccountBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

private lateinit var binding: ActivityCreateAccountBinding
private lateinit var auth: FirebaseAuth
private lateinit var db: FirebaseFirestore

class CreateAccountActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCreateAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Configuração do clique do botão de efetuar cadastro
        binding.btnCriar.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val senha = binding.etSenha.text.toString()
            val confirmarSenha = binding.etConfirmarSenha.text.toString()
            val nome = binding.etNome.text.toString()

            // Verificação dos campos obrigatórios e das regras de validação
            when {
                nome.isEmpty() -> {
                    mensagemNegativa(it, "Preencha seu nome")
                }
                email.isEmpty() -> {
                    mensagemNegativa(it, "Preencha seu email")
                }
                senha.isEmpty() -> {
                    mensagemNegativa(it, "Preencha sua senha")
                }
                senha.length < 6 -> {
                    mensagemNegativa(it, "A senha precisa ter pelo menos seis caracteres")
                }
                senha != confirmarSenha -> {
                    mensagemNegativa(it, "As senhas não coincidem")
                }
                else -> {
                    // Verifica se o email já está em uso
                    db.collection("pessoa")
                        .whereEqualTo("email", email)
                        .get()
                        .addOnSuccessListener { pessoas ->
                            var contaJaExiste = false

                            pessoas.forEach { pessoa ->
                                if (pessoa.getString("email") == email) {
                                    contaJaExiste = true
                                    return@forEach
                                }
                            }

                            if (contaJaExiste) {
                                mensagemNegativa(it, "O email fornecido já está em uso")
                            } else {
                                // Criação da conta no Firebase Auth
                                auth.createUserWithEmailAndPassword(email, senha)
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            // Salva os dados no Firestore
                                            salvarDadosNoFirestore(nome, email, senha)
                                            auth.signInWithEmailAndPassword(email, senha)
                                                .addOnCompleteListener(this) { task ->
                                                    if (task.isSuccessful) {
                                                        // Se o login for bem-sucedido, vai para a tela principal do cliente
                                                        Log.d(ContentValues.TAG, "signInWithEmail:success")

                                                        val user = auth.currentUser
                                                        val userId = user?.uid

                                                        val userDocRef = FirebaseFirestore.getInstance().collection("pessoa").document(userId!!)
                                                        userDocRef.get().addOnSuccessListener { documentSnapshot ->
                                                            if (documentSnapshot.exists()) {
                                                                startActivity(Intent(this, MapsActivity::class.java))
                                                            }
                                                        }
                                                    } else {
                                                        // Se o login falhar, exibe uma mensagem de erro
                                                        Log.w(ContentValues.TAG, "signInWithEmail:failure", task.exception)
                                                        mensagemNegativa(binding.root, "Falha na autenticação, tente novamente")
                                                    }
                                                    this.finish()
                                                }
                                        } else {
                                            Log.w(ContentValues.TAG, "createUserWithEmail:failure", task.exception)
                                            mensagemNegativa(binding.root, "Falha na criação da conta: ${task.exception?.message}")
                                        }
                                    }
                                    .addOnFailureListener { exception ->
                                        Log.w(ContentValues.TAG, "createUserWithEmail:failure", exception)
                                        mensagemNegativa(binding.root, "Falha na criação da conta: ${exception.message}")
                                    }
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.w(ContentValues.TAG, "Firestore query failed", exception)
                            mensagemNegativa(binding.root, "Erro ao verificar o email: ${exception.message}")
                        }
                }
            }
        }
    }

    private fun salvarDadosNoFirestore(nome: String,email: String, senha: String) {
        val pessoaMap = hashMapOf(
            "nome" to nome,
            "email" to email,
            "senha" to senha,
        )

        val user = FirebaseAuth.getInstance().currentUser
        user?.let {
            db.collection("pessoa").document(it.uid)
                .set(pessoaMap)
                .addOnSuccessListener {
                    mensagemPositive(binding.root, "Bem-vindo")
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        baseContext,
                        "Erro ao enviar dados: $e",
                        Toast.LENGTH_SHORT
                    ).show()
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

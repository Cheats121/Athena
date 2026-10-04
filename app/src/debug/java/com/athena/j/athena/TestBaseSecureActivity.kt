package com.athena.j.athena

class TestBaseSecureActivity : BaseSecureActivity() {

    var receivedPassword: String? = null
        private set

    var passwordCallbackInvoked: Boolean = false
        private set

    fun showCreatePasswordDialog() {
        receivedPassword = null
        passwordCallbackInvoked = false

        showNewVaultPasswordDialog { password ->
            receivedPassword = password
            passwordCallbackInvoked = true
        }
    }
}
package com.smartemergency.app

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.smartemergency.app.ContactManager.Contact
import com.smartemergency.app.adapter.ContactsAdapter
import com.smartemergency.app.databinding.ActivityEmergencyContactsBinding

class EmergencyContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmergencyContactsBinding
    private lateinit var adapter: ContactsAdapter

    // Managed via ContactManager
    private var contactsList = mutableListOf<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupAddButton()

        // Load saved contacts
        contactsList.addAll(ContactManager.getContacts(this))
        if (contactsList.isEmpty()) {
            addSampleContacts()
        }
    }

    private fun setupToolbar() {
        binding.toolbarContacts.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = ContactsAdapter(
            contacts = contactsList,
            onEditClick = { position -> showEditContactDialog(position) },
            onDeleteClick = { position -> deleteContact(position) }
        )
        binding.rvContacts.layoutManager = LinearLayoutManager(this)
        binding.rvContacts.adapter = adapter
    }

    private fun setupAddButton() {
        binding.btnAddContact.setOnClickListener {
            showAddContactDialog()
        }
    }

    private fun addSampleContacts() {
        contactsList.add(Contact("Mom", "+91 98765 43210"))
        contactsList.add(Contact("Dad", "+91 98765 43211"))
        contactsList.add(Contact("Best Friend", "+91 87654 32109"))
        contactsList.add(Contact("Shruti", "+91 75583 41221"))

        ContactManager.saveContacts(this, contactsList)
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun showAddContactDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null)
        val dialog = AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_rounded_card)

        val etName = dialogView.findViewById<TextInputEditText>(R.id.et_contact_name)
        val etPhone = dialogView.findViewById<TextInputEditText>(R.id.et_contact_phone)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_cancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_save)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etName?.text?.toString()?.trim() ?: ""
            val phone = etPhone?.text?.toString()?.trim() ?: ""

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            contactsList.add(Contact(name, phone))
            ContactManager.saveContacts(this, contactsList)
            adapter.notifyItemInserted(contactsList.size - 1)
            updateEmptyState()
            dialog.dismiss()
            Toast.makeText(this, "$name added", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showEditContactDialog(position: Int) {
        val contact = contactsList[position]
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null)
        val dialog = AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_rounded_card)

        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tv_dialog_title)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.et_contact_name)
        val etPhone = dialogView.findViewById<TextInputEditText>(R.id.et_contact_phone)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_cancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_save)

        tvTitle?.text = "Edit Contact"
        etName?.setText(contact.name)
        etPhone?.setText(contact.phone)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etName?.text?.toString()?.trim() ?: ""
            val phone = etPhone?.text?.toString()?.trim() ?: ""

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            contactsList[position] = Contact(name, phone)
            ContactManager.saveContacts(this, contactsList)
            adapter.notifyItemChanged(position)
            dialog.dismiss()
            Toast.makeText(this, "$name updated", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun deleteContact(position: Int) {
        val name = contactsList[position].name
        AlertDialog.Builder(this)
            .setTitle("Delete Contact")
            .setMessage("Remove $name from emergency contacts?")
            .setPositiveButton("Delete") { _, _ ->
                contactsList.removeAt(position)
                ContactManager.saveContacts(this, contactsList)
                adapter.notifyItemRemoved(position)
                updateEmptyState()
                Toast.makeText(this, "$name removed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateEmptyState() {
        if (contactsList.isEmpty()) {
            binding.layoutEmptyContacts.visibility = android.view.View.VISIBLE
            binding.rvContacts.visibility = android.view.View.GONE
        } else {
            binding.layoutEmptyContacts.visibility = android.view.View.GONE
            binding.rvContacts.visibility = android.view.View.VISIBLE
        }
    }
}

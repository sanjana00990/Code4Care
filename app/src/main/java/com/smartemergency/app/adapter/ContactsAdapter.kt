package com.smartemergency.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smartemergency.app.EmergencyContactsActivity.Contact
import com.smartemergency.app.databinding.ItemContactBinding

class ContactsAdapter(
    private val contacts: MutableList<Contact>,
    private val onEditClick: (Int) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    inner class ContactViewHolder(val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact, position: Int) {
            // Generate initials for avatar
            val initials = contact.name.split(" ")
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .joinToString("")
            binding.tvContactAvatar.text = initials.ifEmpty { "?" }

            binding.tvContactName.text = contact.name
            binding.tvContactPhone.text = contact.phone

            binding.btnEditContact.setOnClickListener { onEditClick(position) }
            binding.btnDeleteContact.setOnClickListener { onDeleteClick(position) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(contacts[position], position)
    }

    override fun getItemCount(): Int = contacts.size
}

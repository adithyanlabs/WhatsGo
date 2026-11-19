package com.whatsgo.app.core

data class GroupMember(
    val jid: String,
    val name: String,
    val isAdmin: Boolean,
    val isSuperAdmin: Boolean,
    val isMe: Boolean
)

data class GroupInfoData(
    val jid: String,
    val name: String,
    val description: String,
    val descriptionBy: String,
    val descriptionAt: Long,
    val createdAt: Long,
    val createdBy: String,
    val locked: Boolean,
    val announce: Boolean,
    val adminAddOnly: Boolean,
    val ephemeralSeconds: Long,
    val isCommunity: Boolean,
    val inviteLink: String,
    val members: List<GroupMember>
) {
    val memberCount: Int get() = members.size
}
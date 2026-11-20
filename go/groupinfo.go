package engine

import (
	"context"
	"encoding/json"
	"fmt"

	"go.mau.fi/whatsmeow/types"
)

type groupMemberJSON struct {
	JID        string `json:"jid"`
	Name       string `json:"name"`
	Saved      bool   `json:"saved"`
	Admin      bool   `json:"admin"`
	SuperAdmin bool   `json:"superAdmin"`
}

type groupInfoJSON struct {
	JID          string            `json:"jid"`
	Name         string            `json:"name"`
	Topic        string            `json:"topic"`
	TopicSetBy   string            `json:"topicSetBy"`
	TopicSetAt   int64             `json:"topicSetAt"`
	Owner        string            `json:"owner"`
	Created      int64             `json:"created"`
	Locked       bool              `json:"locked"`
	Announce     bool              `json:"announce"`
	Ephemeral    bool              `json:"ephemeral"`
	Timer        int64             `json:"timer"`
	AdminAddOnly bool              `json:"adminAddOnly"`
	IsParent     bool              `json:"isParent"`
	Invite       string            `json:"invite"`
	Members      []groupMemberJSON `json:"members"`
}

func (c *Client) personName(j types.JID) string {
	if j.IsEmpty() {
		return ""
	}
	return c.GetContactName(c.normJID(j).String())
}

func (c *Client) GetGroupInfoJSON(groupJID string) (string, error) {
	if c.inner == nil || c.inner.Store.ID == nil {
		return "", ErrNotLoggedInLocal
	}
	jid, err := types.ParseJID(groupJID)
	if err != nil {
		return "", err
	}
	info, err := c.inner.GetGroupInfo(context.Background(), jid)
	if err != nil {
		return "", err
	}
	if info == nil {
		return "", fmt.Errorf("group not found")
	}

	out := groupInfoJSON{
		JID:          info.JID.String(),
		Name:         info.Name,
		Topic:        info.Topic,
		Locked:       info.IsLocked,
		Announce:     info.IsAnnounce,
		Ephemeral:    info.IsEphemeral,
		Timer:        int64(info.DisappearingTimer),
		AdminAddOnly: string(info.MemberAddMode) == "admin_add",
		IsParent:     info.IsParent,
		Members:      make([]groupMemberJSON, 0, len(info.Participants)),
	}
	if !info.OwnerJID.IsEmpty() {
		out.Owner = c.normJID(info.OwnerJID).String()
	}
	if !info.TopicSetBy.IsEmpty() {
		out.TopicSetBy = c.normJID(info.TopicSetBy).String()
	}
	if !info.TopicSetAt.IsZero() {
		out.TopicSetAt = info.TopicSetAt.Unix()
	}
	if !info.GroupCreated.IsZero() {
		out.Created = info.GroupCreated.Unix()
	}

	// one local read for all members, not one per member
	all, _ := c.inner.Store.Contacts.GetAllContacts(context.Background())

	for _, p := range info.Participants {
		j := p.JID
		if !p.PhoneNumber.IsEmpty() {
			j = p.PhoneNumber
		}
		j = c.normJID(j)

		name, saved := "", false
		if ci, ok := all[j]; ok {
			switch {
			case ci.FullName != "":
				name, saved = ci.FullName, true
			case ci.FirstName != "":
				name, saved = ci.FirstName, true
			default:
				name = ci.PushName
			}
		}

		out.Members = append(out.Members, groupMemberJSON{
			JID:        j.String(),
			Name:       name,
			Saved:      saved,
			Admin:      p.IsAdmin || p.IsSuperAdmin,
			SuperAdmin: p.IsSuperAdmin,
		})
	}

	if link, err := c.inner.GetGroupInviteLink(context.Background(), jid, false); err == nil {
		out.Invite = link
	}

	b, err := json.Marshal(out)
	return string(b), err
}
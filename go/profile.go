package engine

import (
	"context"
	"errors"

	wa "go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/types"
)

func (c *Client) GetOwnPhone() string {
	if c.inner.Store.ID == nil {
		return ""
	}
	return c.inner.Store.ID.User
}

func (c *Client) GetOwnName() string {
	return c.inner.Store.PushName
}

func (c *Client) GetOwnJID() string {
	if c.inner.Store.ID == nil {
		return ""
	}
	return c.inner.Store.ID.String()
}

func (c *Client) FetchOwnProfilePictureURL() (string, error) {
	if c.inner.Store.ID == nil {
		return "", nil
	}
	jid := c.inner.Store.ID.ToNonAD()
	info, err := c.inner.GetProfilePictureInfo(context.Background(), jid, &wa.GetProfilePictureParams{})
	if err != nil {
		if errors.Is(err, wa.ErrProfilePictureNotSet) || errors.Is(err, wa.ErrProfilePictureUnauthorized) {
			return "", nil
		}
		return "", err
	}
	if info == nil {
		return "", nil
	}
	return info.URL, nil
}

func (c *Client) GetContactName(jid string) string {
	parsed, err := types.ParseJID(jid)
	if err != nil {
		return ""
	}
	contact, err := c.inner.Store.Contacts.GetContact(context.Background(), parsed)
	if err != nil || !contact.Found {
		return ""
	}
	if contact.FullName != "" {
		return contact.FullName
	}
	if contact.FirstName != "" {
		return contact.FirstName
	}
	return contact.PushName
}

func (c *Client) GetGroupName(jid string) string {
	parsed, err := types.ParseJID(jid)
	if err != nil {
		return ""
	}
	info, err := c.inner.GetGroupInfo(context.Background(), parsed)
	if err != nil || info == nil {
		return ""
	}
	return info.Name
}

func (c *Client) IsGroupJID(jid string) bool {
	parsed, err := types.ParseJID(jid)
	if err != nil {
		return false
	}
	return parsed.Server == types.GroupServer
}

func (c *Client) GetProfilePictureURL(jidStr string) (string, error) {
	if c.inner.Store.ID == nil {
		return "", ErrNotLoggedInLocal
	}

	jid, err := types.ParseJID(jidStr)
	if err != nil {
		return "", err
	}

	info, err := c.inner.GetProfilePictureInfo(
		context.Background(),
		jid,
		&wa.GetProfilePictureParams{Preview: true},
	)
	if err != nil {
		if errors.Is(err, wa.ErrProfilePictureNotSet) ||
			errors.Is(err, wa.ErrProfilePictureUnauthorized) {
			return "", nil
		}
		return "", err
	}

	if info == nil || info.URL == "" {
		return "", nil
	}

	return info.URL, nil
}

func (c *Client) GetContactAbout(jidStr string) string {
	if c.inner.Store.ID == nil {
		return ""
	}
	jid, err := types.ParseJID(jidStr)
	if err != nil {
		return ""
	}
	nonAD := jid.ToNonAD()
	info, err := c.inner.GetUserInfo(context.Background(), []types.JID{nonAD})
	if err != nil {
		return ""
	}
	if ui, ok := info[nonAD]; ok {
		return ui.Status
	}
	return ""
}
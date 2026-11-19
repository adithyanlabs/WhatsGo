package engine

import (
	"context"

	"go.mau.fi/whatsmeow/types"
)

func (c *Client) normJID(j types.JID) types.JID {
	j = j.ToNonAD()
	if j.Server == types.HiddenUserServer && c.inner.Store != nil && c.inner.Store.LIDs != nil {
		if pn, err := c.inner.Store.LIDs.GetPNForLID(context.Background(), j); err == nil && !pn.IsEmpty() {
			return pn
		}
	}
	return j
}

func (c *Client) normJIDStr(s string) string {
	if s == "" {
		return s
	}
	jid, err := types.ParseJID(s)
	if err != nil {
		return s
	}
	return c.normJID(jid).String()
}
package engine

import (
	"context"

	"go.mau.fi/whatsmeow/types"
)

func (c *Client) normJID(jid types.JID) types.JID {
	if jid.Server != types.HiddenUserServer {
		return jid
	}
	pn, err := c.inner.Store.LIDs.GetPNForLID(context.Background(), jid)
	if err != nil || pn.IsEmpty() {
		return jid
	}
	return pn
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
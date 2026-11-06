package engine

import (
	"context"
	"encoding/json"
)

type groupRow struct {
	Jid  string `json:"jid"`
	Name string `json:"name"`
}

func (c *Client) GetJoinedGroupsJSON() (string, error) {
	if c.inner.Store.ID == nil {
		return "", ErrNotLoggedInLocal
	}
	groups, err := c.inner.GetJoinedGroups(context.Background())
	if err != nil {
		return "", err
	}
	out := make([]groupRow, 0, len(groups))
	for _, g := range groups {
		if g == nil {
			continue
		}
		out = append(out, groupRow{Jid: g.JID.String(), Name: g.Name})
	}
	b, err := json.Marshal(out)
	if err != nil {
		return "", err
	}
	return string(b), nil
}
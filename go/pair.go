package engine

import (
	"context"

	wa "go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/types/events"
)

func (c *Client) StartQrLogin() error {
	if c.inner.Store.ID != nil {
		return c.inner.Connect()
	}
	if c.inner.IsConnected() {
		return nil
	}

	qrChan, err := c.inner.GetQRChannel(context.Background())
	if err != nil {
		return err
	}
	if err := c.inner.Connect(); err != nil {
		return err
	}

	for evt := range qrChan {
		switch evt.Event {
		case "code":
			c.cb.OnQrCode(evt.Code)
		case "success":
			connectedDone := make(chan struct{})
			handlerID := c.inner.AddEventHandler(func(e interface{}) {
				if _, ok := e.(*events.Connected); ok {
					select {
					case <-connectedDone:
					default:
						close(connectedDone)
					}
				}
			})
			<-connectedDone
			c.inner.RemoveEventHandler(handlerID)
			c.cb.OnConnected()
			return nil
		case "timeout":
			c.cb.OnError("qr_timeout")
		}
	}
	return nil
}

func (c *Client) StartPairingLogin(phoneDigits string) error {
	if c.inner.Store.ID != nil {
		return c.inner.Connect()
	}

	if !c.inner.IsConnected() {
		qrChan, err := c.inner.GetQRChannel(context.Background())
		if err != nil {
			return err
		}
		if err := c.inner.Connect(); err != nil {
			return err
		}
		<-qrChan
	}

	code, err := c.inner.PairPhone(
		context.Background(),
		phoneDigits,
		true,
		wa.PairClientChrome,
		"Chrome (Linux)",
	)
	if err != nil {
		c.cb.OnError(err.Error())
		return err
	}
	c.cb.OnPairingCode(code)

	pairDone := make(chan struct{})
	var pairErr error
	pairHandlerID := c.inner.AddEventHandler(func(evt interface{}) {
		switch e := evt.(type) {
		case *events.PairSuccess:
			select {
			case <-pairDone:
			default:
				close(pairDone)
			}
		case *events.PairError:
			pairErr = e.Error
			select {
			case <-pairDone:
			default:
				close(pairDone)
			}
		}
	})
	<-pairDone
	c.inner.RemoveEventHandler(pairHandlerID)
	if pairErr != nil {
		c.cb.OnError(pairErr.Error())
		return pairErr
	}

	connectedDone := make(chan struct{})
	connHandlerID := c.inner.AddEventHandler(func(evt interface{}) {
		if _, ok := evt.(*events.Connected); ok {
			select {
			case <-connectedDone:
			default:
				close(connectedDone)
			}
		}
	})
	<-connectedDone
	c.inner.RemoveEventHandler(connHandlerID)

	return nil
}
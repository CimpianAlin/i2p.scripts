#!/bin/sh
java -cp $I2P/lib/router.jar:$I2P/lib/i2p.jar net.i2p.data.Destination "$@"

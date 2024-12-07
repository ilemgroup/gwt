#!/bin/bash

ant clean dist-dev -Dgwt.version=2.12.1.ilem
maven/push-gwtproject.sh
cp build/lib/gwt-servlet-jakarta.jar ../../3rdParty/gwt-2.12.1-ilem
cp build/lib/gwt-user.jar ../../3rdParty/gwt-2.12.1-ilem/initial
cp build/lib/gwt-dev.jar ../../3rdParty/gwt-2.12.1-ilem/initial
cp build/lib/gwt-servlet.jar ../../3rdParty/gwt-2.12.1-ilem/initial
cp build/lib/gwt-codeserver.jar ../../3rdParty/gwt-2.12.1-ilem/initial
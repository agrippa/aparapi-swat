#!/bin/bash

pushd com.amd.aparapi
ant clean
PATH=/usr/bin/:$PATH ant -Damd.app.sdk.dir=$CL_HOME
popd

#!/bin/bash

ant clean
ant clean
PATH=/usr/bin/:$PATH ant -Damd.app.sdk.dir=$CUDA_HOME

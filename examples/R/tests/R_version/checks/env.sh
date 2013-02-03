#!/bin/bash

echo "ENV"

env | grep JOB_ |sort

echo =============================
echo STDOUT

cat $1

echo =============================
echo FILESIZE
echo $2


echo =============================
echo EXISTS
echo $3

echo ============================
echo CONTENT
echo $4
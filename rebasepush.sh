#!/bin/sh
set -e

echo "Pushing to general AVIO Nexus"
mvn clean deploy

echo "Now updating customer code"
git checkout customer_branch_name
git rebase master
git push --force customer_origin customer_branch_name:master

echo "Deploying to customer"
mvn clean deploy

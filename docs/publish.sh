#!/bin/bash

rsync -avz --delete --exclude '.git' --exclude '.idea' --exclude '*.iml' --exclude '.gitignore' _site/ ../../datakernel.io

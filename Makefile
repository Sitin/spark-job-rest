CURRENT_DIR := $(shell pwd)

#
# Deployment configuration
#
# Deploy script
DEPLOY_SCRIPT = $(CURRENT_DIR)/src/main/resources/deploy.sh
#
# Override this to set deploy path
SJR_DEPLOY_PATH ?= $(CURRENT_DIR)/deploy-test

#
# We strongly suggest not to keep remote deployment configuration variables out of Git control!
#
# Overrides SJR_DEPLOY_PATH in remote deploy mode if not empty
SJR_REMOTE_DEPLOY_PATH ?=
#
# Set this the [user]@hostname of the machine you want to deploy to
SJR_DEPLOY_HOST ?=
#
# Optionally set path to SSH key here
SJR_DEPLOY_KEY ?=

#
# Build versioning
#
BUILD_MARK ?= DEV-`git rev-parse --abbrev-ref HEAD | sed -e 's:/:-:g'`
#
SJR_VERSION ?= $(shell BUILD_MARK=$(BUILD_MARK) sbt --error 'set showSuccess := false' projectVersion)

#
# Remote deployment parameters
#
REMOTE_PARAMS := SJR_DEPLOY_PATH=$(SJR_DEPLOY_PATH) \
                 SJR_DEPLOY_HOST=$(SJR_DEPLOY_HOST) \
                 SJR_DEPLOY_KEY=$(SJR_DEPLOY_KEY) \
                 SJR_IS_REMOTE_DEPLOY="true" \
                 SJR_REMOTE_DEPLOY_PATH=$(SJR_REMOTE_DEPLOY_PATH)

all: remove build deploy

build:
	@sbt clean package bundle

incremental-build:
	@sbt package bundle

publish:
	@BUILD_MARK=$(BUILD_MARK) sbt publish -Dsbt.log.noformat=true

deploy:
	@SJR_DEPLOY_PATH=$(SJR_DEPLOY_PATH) \
	$(DEPLOY_SCRIPT) deploy

remote-deploy:
	@$(REMOTE_PARAMS) $(DEPLOY_SCRIPT) deploy

remote-start:
	@$(REMOTE_PARAMS) $(DEPLOY_SCRIPT) start

remote-stop:
	@$(REMOTE_PARAMS) $(DEPLOY_SCRIPT) stop

remote-log:
	@$(REMOTE_PARAMS) $(DEPLOY_SCRIPT) log

start: stop
	@SJR_DEPLOY_PATH=$(SJR_DEPLOY_PATH) \
    $(DEPLOY_SCRIPT) start

stop:
	@SJR_DEPLOY_PATH=$(SJR_DEPLOY_PATH) \
    $(DEPLOY_SCRIPT) stop

remove:
	@SJR_DEPLOY_PATH=$(SJR_DEPLOY_PATH) \
	$(DEPLOY_SCRIPT) remove

version:
	@echo $(SJR_VERSION)

.PHONY: all build deploy
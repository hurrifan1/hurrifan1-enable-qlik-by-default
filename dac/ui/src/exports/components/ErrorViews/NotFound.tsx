/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Link } from "react-router";
import { Button } from "dremio-ui-lib/dist-esm/index";
import { intl } from "@app/utils/intl";
import { ErrorView } from "./ErrorView";
import * as orgPaths from "dremio-ui-common/paths/organization.js";
import * as commonPaths from "dremio-ui-common/paths/common.js";
//@ts-ignore
import narwhal404 from "dremio-ui-lib/icons/dremio/narwhal/narwhal-404.svg";
import { FeatureSwitch } from "../FeatureSwitch/FeatureSwitch";
import { ORGANIZATION_LANDING } from "../../flags/ORGANIZATION_LANDING";
import { getSonarContext } from "dremio-ui-common/contexts/SonarContext.js";

export const NotFound = ({
  title,
  action,
}: {
  title?: string;
  action?: any;
}) => {
  const { formatMessage } = intl;
  const projectId = getSonarContext()?.getSelectedProjectId?.();
  return (
    <ErrorView
      title={title ?? formatMessage({ id: "404.ThePageDoesntExist" })}
      image={<img src={narwhal404} alt="" />}
      action={
        <>
          <FeatureSwitch
            flag={ORGANIZATION_LANDING}
            renderEnabled={() =>
              action ?? (
                <Button
                  as={Link}
                  variant="primary"
                  to={orgPaths.organization.link()}
                >
                  {formatMessage({ id: "404.GoToConsole" })}
                </Button>
              )
            }
            renderDisabled={() => (
              <Button
                as={Link}
                variant="primary"
                to={commonPaths.projectBase({ projectId })}
              >
                {formatMessage({ id: "404.GoToHome" })}
              </Button>
            )}
          />
        </>
      }
    />
  );
};

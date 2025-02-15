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
package com.dremio.dac.model.job.acceleration;

import static com.dremio.dac.model.job.acceleration.UiMapper.toUI;

import java.util.List;

import com.dremio.dac.proto.model.acceleration.LayoutApiDescriptor;
import com.dremio.dac.resource.ApiIntentMessageMapper;
import com.dremio.sabot.kernel.proto.ReflectionExplanation;
import com.dremio.service.accelerator.proto.ReflectionRelationship;
import com.dremio.service.accelerator.proto.SubstitutionState;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

/**
 * UI wrapper for {@link ReflectionRelationship}
 */
public class ReflectionRelationshipUI {
  private final SubstitutionState relationship;
  private final MaterializationDetailsUI materialization;
  private final DatasetDetailsUI dataset;
  private final AccelerationSettingsUI accelerationSettings;
  private final LayoutApiDescriptor reflection;
  private final boolean snowflake;
  private final double hintScore;
  private final List<ReflectionExplanationUI> reflectionHints;

  @JsonCreator
  ReflectionRelationshipUI(
    @JsonProperty("relationship") SubstitutionState relationship,
    @JsonProperty("materialization") MaterializationDetailsUI materialization,
    @JsonProperty("dataset") DatasetDetailsUI dataset,
    @JsonProperty("accelerationSettings") AccelerationSettingsUI accelerationSettings,
    @JsonProperty("reflection") LayoutApiDescriptor reflection,
    @JsonProperty("snowflake") boolean snowflake,
    @JsonProperty("hintScore") double hintScore,
    @JsonProperty("reflectionHints") List<ReflectionExplanationUI> reflectionHints) {
    this.relationship = relationship;
    this.materialization = materialization;
    this.dataset = dataset;
    this.accelerationSettings = accelerationSettings;
    this.reflection = reflection;
    this.snowflake = snowflake;
    this.hintScore = hintScore;
    this.reflectionHints = reflectionHints;
  }

  ReflectionRelationshipUI(ReflectionRelationship reflectionRelationship) {
    this(reflectionRelationship.getState(),
      toUI(reflectionRelationship.getMaterialization()),
      toUI(reflectionRelationship.getDataset()),
      toUI(reflectionRelationship.getAccelerationSettings()),
      ApiIntentMessageMapper.toApiMessage(reflectionRelationship.getReflection(), reflectionRelationship.getReflectionType()),
      reflectionRelationship.getSnowflake(),
      reflectionRelationship.getQueryDistance(),
      transformList(reflectionRelationship.getReflectionExplanationList()));
  }



  public SubstitutionState getRelationship() {
    return relationship;
  }

  public MaterializationDetailsUI getMaterialization() {
    return materialization;
  }

  public DatasetDetailsUI getDataset() {
    return dataset;
  }

  public AccelerationSettingsUI getAccelerationSettings() {
    return accelerationSettings;
  }

  public LayoutApiDescriptor getReflection() {
    return reflection;
  }

  public boolean isSnowflake() {
    return snowflake;
  }

  public double getHintScore() {
    return hintScore;
  }

  public List<ReflectionExplanationUI> getReflectionHints() {
    return reflectionHints;
  }

  private static List<ReflectionExplanationUI> transformList(List<ReflectionExplanation> reflectionHints){
    if(null == reflectionHints){
      return null;
    }
    ImmutableList.Builder<ReflectionExplanationUI> results = ImmutableList.builder();
    for (ReflectionExplanation rh: reflectionHints){
      results.add(toUI(rh));
    }
    return results.build();
  }
}

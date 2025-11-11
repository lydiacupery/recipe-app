package com.lcupery.recipe_app.mapper;

import com.lcupery.recipe_app.dto.StepDto;
import com.lcupery.recipe_app.entity.Step;

public class StepMapper {
    public static StepDto mapToStepDto(Step step) {
        StepDto stepDto = new StepDto();
        stepDto.setId(step.getId());
        stepDto.setStepNumber(step.getStepNumber());
        stepDto.setInstruction(step.getInstruction());
        return stepDto;
    }

    public static Step mapToStep(StepDto stepDto) {
        Step step = new Step();
        step.setId(stepDto.getId());
        step.setStepNumber(stepDto.getStepNumber());
        step.setInstruction(stepDto.getInstruction());
        return step;
    }
}

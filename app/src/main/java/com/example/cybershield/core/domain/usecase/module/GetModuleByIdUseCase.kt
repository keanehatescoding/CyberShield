package com.example.cybershield.core.domain.usecase.module

import com.example.cybershield.core.domain.model.Module
import com.example.cybershield.core.domain.repository.ModuleRepository
import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetModuleByIdUseCase @Inject constructor(
    private val moduleRepository: ModuleRepository,
) {
    operator fun invoke(moduleId: String): Flow<Result<Module>> =
        moduleRepository.getModuleById(moduleId)
}
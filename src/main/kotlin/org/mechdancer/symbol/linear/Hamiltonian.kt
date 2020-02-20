package org.mechdancer.symbol.linear

import org.mechdancer.symbol.Differential
import org.mechdancer.symbol.Expression
import org.mechdancer.symbol.div

/** 哈密顿算子（梯度算子） */
inline class Hamiltonian(private val space: VariableSpace) {
    /** 求 [f] 数量场在此变量空间上的梯度 */
    operator fun times(f: Expression): Field {
        val df = f.d()
        return Field(space.variables.associateWith { df / Differential(it) })
    }
}

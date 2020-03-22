package org.mechdancer.symbol.linear

import org.mechdancer.symbol.core.Expression
import org.mechdancer.symbol.core.VariableSpace

/** 海森算子 */
inline class Hessian(val space: VariableSpace) {
    /** 求 [f] 数量场在此变量空间上的海森矩阵 */
    operator fun times(f: Expression) =
        HessianMatrix.hessian(f.d().d(), space)
}

package org.mechdancer.symbol.core

import org.mechdancer.symbol.core.Constant.Companion.`-1`
import org.mechdancer.symbol.core.Constant.Companion.`1`
import org.mechdancer.symbol.core.Constant.Companion.`0`
import org.mechdancer.symbol.mapParallel
import org.mechdancer.symbol.sumParallel
import kotlin.math.sign

/** 运算 */
sealed class Calculation : FunctionExpression {
    protected abstract fun timesWithoutCheck(c: Constant): Expression
    protected abstract fun divWithoutCheck(c: Constant): Expression
    protected abstract fun format(which: (Expression) -> String): String

    final override fun times(c: Constant) =
        when (c) {
            `0`  -> `0`
            `1`  -> this
            else -> timesWithoutCheck(c)
        }

    final override fun div(c: Constant) =
        when (c) {
            `0`  -> Constant.NaN
            `1`  -> this
            else -> divWithoutCheck(c)
        }

    final override fun toString() = format(Expression::toString)
    final override fun toTex() = format(Expression::toTex)
}

// 和式合并器，不支持非顶层的 typealias，只能放在这里
private typealias SumCollector = HashMap<Expression, Constant>

// 合并算法，同类项系数相加或同底幂函数指数相加
private fun <T : Expression> HashMap<T, Constant>.merge(e: T, b: Constant) {
    compute(e) { _, a -> (a?.plus(b) ?: b).takeIf { it != `0` } }
}

/** 和式 */
class Sum private constructor(
    internal val products: Set<ProductExpression>,
    internal val tail: Constant
) : Calculation(),
    BaseExpression,
    LnExpression {
    override fun d() =
        if (products.size > parallelism)
            products.mapParallel(Expression::d).let(::get)
        else
            products.map(Expression::d).let(::get)

    override fun substitute(from: Expression, to: Expression) =
        if (this == from) to else get(products.map { it.substitute(from, to) }) + tail

    override fun substitute(map: Map<out FunctionExpression, Expression>) =
        map[this] ?: get(products.map { it.substitute(map) }) + tail

    override fun toFunction(v: Variable) =
        products.map { it.toFunction(v) }.parallel(tail)

    override fun toFunction(space: VariableSpace) =
        products.map { it.toFunction(space) }.parallel(tail)

    override fun equals(other: Any?) =
        this === other || other is Sum && tail == other.tail && products == other.products

    override fun hashCode() = products.hashCode() xor tail.hashCode()

    override fun format(which: (Expression) -> String) =
        buildString {
            append(which(products.first()))
            for (item in products.asSequence().drop(1))
                when (item) {
                    is Product -> append(item.toStringAsComponent())
                    else       -> append(" + ${which(item)}")
                }
            when {
                tail > `0` -> append(" + ${which(tail)}")
                tail < `0` -> append(" - ${which(-tail)}")
            }
        }

    override fun plus(c: Constant) = Sum(products, tail + c)
    override fun minus(c: Constant) = Sum(products, tail - c)
    override fun timesWithoutCheck(c: Constant) = Sum(products.map { (it * c) as ProductExpression }.toSet(), tail * c)
    override fun divWithoutCheck(c: Constant) = Sum(products.map { (it / c) as ProductExpression }.toSet(), tail / c)

    companion object Builder {
        operator fun get(vararg e: Expression) = get(e.asList())

        operator fun get(list: Collection<Expression>) =
            when (list.size) {
                0    -> `0`
                1    -> list.first()
                else -> {
                    // 合并同类项
                    val collector = hashMapOf<Expression, Constant>()
                    for (e in list) collector += e
                    // 所谓常数就是 1 的倍数
                    val tail = collector.remove(`1`) ?: `0`
                    // 积式与常数相乘必然还是积式
                    val products = collector
                        .asSequence()
                        .map { (e, k) -> Product[e, k] as ProductExpression }
                        .toSet()
                    when {
                        products.isEmpty()                -> tail
                        tail == `0` && products.size == 1 -> products.first()
                        else                              -> Sum(products, tail)
                    }
                }
            }

        private operator fun SumCollector.plusAssign(e: Expression) {
            fun inner(p: ProductExpression): Unit =
                when (p) {
                    is FactorExpression -> merge(p, `1`) // {var, factor = d_, {pow, exp, ln}}
                    is Product          -> merge(p.resetTimes(`1`), p.times)
                    else                -> throw UnsupportedOperationException()
                }

            when (e) {
                `0`                  -> Unit
                is Constant          -> merge(`1`, e)
                is ProductExpression -> inner(e) // {var, product = {factor = d_, {pow, exp, ln}, product}}
                is Sum               -> {
                    for (p in e.products) inner(p)
                    merge(`1`, e.tail)
                }
                else                 -> throw UnsupportedOperationException()
            }
        }

        private fun <T> List<(T) -> Double>.parallel(tail: Constant) =
            if (size > parallelism)
                { t: T -> sumParallel { it(t) } + tail.re }
            else
                { t: T -> sumByDouble { it(t) } + tail.re }
    }
}

/** 积式 */
class Product private constructor(
    internal val factors: Set<FactorExpression>,
    internal val times: Constant
) : Calculation(),
    ProductExpression,
    ExponentialExpression,
    LnExpression {
    override fun d() =
        factors
            .withIndex()
            .map { (i, fi) ->
                get(factors.toMutableList<Expression>().apply { removeAt(i); add(fi.d()) })
            }
            .let(Sum.Builder::get) * times

    override fun substitute(from: Expression, to: Expression) =
        if (this == from) to else get(factors.map { it.substitute(from, to) }) * times

    override fun substitute(map: Map<out FunctionExpression, Expression>) =
        map[this] ?: get(factors.map { it.substitute(map) }) * times

    override fun toFunction(v: Variable) =
        factors.map { it.toFunction(v) }.parallel(times)

    override fun toFunction(space: VariableSpace) =
        factors.map { it.toFunction(space) }.parallel(times)

    override fun equals(other: Any?) =
        this === other || other is Product && times == other.times && factors == other.factors

    override fun hashCode() = factors.hashCode() xor times.hashCode()

    override fun format(which: (Expression) -> String) =
        buildString {
            when (times) {
                `1`  -> Unit
                `-1` -> append("-")
                else -> append("${which(times)} ")
            }
            val groups = factors.groupBy(Builder::isDifferential)
            groups[false]?.let { append(it.joinToString(" ", transform = which)) }
            if (groups.size == 2) append(" ")
            groups[true]?.let { append(it.joinToString(" ", transform = which)) }
        }

    internal fun toStringAsComponent(): String {
        val sign = when {
            times.re > 0 -> '+'
            times.re < 0 -> '-'
            times.im > 0 -> '+'
            times.im < 0 -> '-'
            else         -> throw RuntimeException()
        }
        val k = if (sign == '+') times else -times
        return buildString {
            append(" $sign ")
            if (k != `1`) append("${times.toStringAsComponent()} ")
            val groups = factors.groupBy(Builder::isDifferential)
            groups[false]?.let { append(it.joinToString(" ")) }
            if (groups.size == 2) append(" ")
            groups[true]?.let { append(it.joinToString(" ")) }
        }
    }

    internal fun resetTimes(new: Constant) =
        if (new == `1` && factors.size == 1) factors.first() else Product(factors, new)

    override fun timesWithoutCheck(c: Constant): Expression = resetTimes(times * c)
    override fun divWithoutCheck(c: Constant): Expression = resetTimes(times / c)

    companion object Builder {
        operator fun get(vararg e: Expression) = get(e.asList())

        operator fun get(list: Collection<Expression>): Expression {
            return when (list.size) {
                0    -> throw UnsupportedOperationException()
                1    -> list.first()
                else -> {
                    val products = mutableListOf(ProductCollector())
                    for (e in list) {
                        when (e) {
                            `0`                  -> return `0`
                            `1`                  -> Unit
                            is Constant          -> products.removeIf { it *= e; it.isZero() }
                            is ProductExpression -> products.removeIf { it *= e; it.isZero() }
                            is Sum               -> {
                                val copy = products.toList()
                                products.clear()
                                for (a in copy) {
                                    products += e.products.mapNotNull { (a * it).takeUnless(ProductCollector::isZero) }
                                    if (e.tail.re != .0) products += a * e.tail
                                }
                            }
                            else                 -> throw UnsupportedOperationException()
                        }
                        if (products.isEmpty()) return `0`
                    }
                    Sum[products.map { it.build() }]
                }
            }
        }

        // 作为导数算子的阶数
        private fun isDifferential(p: ProductExpression) =
            p is Differential || p is Power && p.member is Differential

        private class ProductCollector private constructor(
            private var tail: Constant,
            powers: Map<BaseExpression, Constant>
        ) {
            private val powers = HashMap(powers)
            private val differentials = powers.keys.filterIsInstance<Differential>().toHashSet()

            fun isZero() = tail == `0`

            operator fun timesAssign(c: Constant) {
                tail *= c
            }

            operator fun timesAssign(e: ProductExpression) {
                when (e) {
                    is FactorExpression -> inner(e) // {var, d_, factor = {pow, exp, ln}}
                    is Product          -> if (e.factors.all { inner(it); tail != `0` }) tail *= e.times
                    else                -> throw UnsupportedOperationException()
                }
            }

            constructor() : this(`1`, emptyMap())

            operator fun times(b: Constant) = ProductCollector(tail * b, powers)
            operator fun times(b: ProductExpression) = ProductCollector(tail, powers).also { it *= b }

            fun build(): Expression {
                // if (tail == .0) return zero // 实际上不必做这个检查，若系数为 0，外部将直接消去
                val products = powers.map { (e, k) -> Power[e, k] }
                return when {
                    products.isEmpty()                -> tail
                    tail == `1` && products.size == 1 -> products.first()
                    else                              -> {
                        val groups = products.groupBy { it is FactorExpression }
                        val product = groups[true]?.let { factors ->
                            if (tail == `1` && factors.size == 1)
                                factors.first()
                            else
                                Product(factors.map { it as FactorExpression }.toSet(), tail)
                        } ?: tail
                        groups[false]?.let { Product[Product[it], product] } ?: product
                    }
                }
            }

            // 认为两个独立变量总是无关，du / dv === 0，检查求导导致的消去
            // 参数 [dv] 是此次迭代改变指数的微元
            private fun check(dv: Differential) {
                // 检查微元的符号
                val nn = powers[dv]?.re?.sign
                when {
                    // 微元已经消去，则离开相关微元集
                    nn == null
                    -> differentials -= dv
                    // 如果所有其他微元同号，新微元加入相关微元集
                    differentials.all { powers[it]?.re?.sign == nn }
                    -> differentials += dv
                    // 否则因子直接置 0，将在外部循环中消去
                    else
                    -> tail = `0`
                }
            }

            // 处理乘以因子，独立函数以避免递归
            private fun inner(e: FactorExpression) {
                when (e) {
                    is Differential   -> {
                        powers.merge(e, `1`)
                        check(e)
                    }
                    is BaseExpression -> // {var, exp, ln}
                        powers.merge(e, `1`)
                    is Power          -> {
                        powers.merge(e.member, e.exponent)
                        (e.member as? Differential)?.let(::check)
                    }
                    else              -> throw UnsupportedOperationException()
                }
            }
        }

        private fun <T> List<(T) -> Double>.parallel(times: Constant) =
            if (size > parallelism)
                { t: T ->
                    parallelStream()
                        .mapToDouble { it(t) }
                        .reduce(times.re) { product, it -> product * it }
                }
            else
                { t: T ->
                    fold(times.re) { product, it -> product * it(t) }
                }
    }
}

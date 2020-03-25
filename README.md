# 符号计算器

![Build on GitHub Action](https://github.com/MechDancer/symbolcalculator/workflows/Build%20with%20Gradle/badge.svg)
![Publish on GitHub Package](https://github.com/MechDancer/symbolcalculator/workflows/Publish%20with%20Gradle/badge.svg)

实现了初等函数的符号运算，现已支持：
- 部分基本初等函数：常数函数、幂函数、指数函数、对数函数

- 部分初等函数：对上述基本初等函数进行加、减、乘、除和复合运算

- 上述初等函数的微分和偏导数计算

- 表达式代换

- 无约束优化（求表达式极小值点）

- 不等式约束优化

  > 不是内点法，效果可能不对

## 使用指南

### 类型

库中重要的类型包括：

- 表达式 `Expression`

  表达式是所有受支持的可微分表达式集合中的元素。

- 变量 `Variable`

  变量指示运算中的符号元素。

### 录入和构造

借助丰富的扩展函数，用户可自然地录入表达式：

- 定义变量

  ```kotlin
  val x by variable // x 是一个名为 "x" 的变量
  val y by variable // y 是一个名为 "y" 的变量
  val variable by variable // variable 是一个名为 "variable" 的变量
  val t = Variable("ttt") // t 是一个名为 "ttt" 的变量
  
  // points 是包含 {x1, y1, z1, x2, ... , z5} 这 15 个变量的变量空间
  val points by variableSpace("x", "y", "z", indices = 1..5)
  
  // xn 是包含 {xa, xb, xc} 这 3 个变量的变量空间
  val xn by variableSpace("x", indices = listOf("a", "b", "c"))
  ```

  > 变量空间：变量空间是用于求梯度的辅助类型，多元数量函数 `y = f(x, y, z, ...)` 可以看作一个空间 `{x, y, z, ...}` 上的数量场，而其在空间 `{x, y, z, ...}` 上的梯度表示为 `{∂f/∂x, ∂f/∂y, ∂f/∂z, ...}`，如果把某个变量 `x` 视作参数，也可求其在空间 `{y, z, ...}` 上的梯度 `{∂f(x)/∂y, ∂f(x)/∂z, ...}`。因此，必须指明在哪个空间上，梯度才有意义。

- 定义表达式

  下面是一些表达式的示例：

  ```kotlin
  val x by variable
  val y by variable
  
  val f1 = 9 * x * x * y + 7 * x * x + 4 * y - 2
  val f2 = ln(9 * x - 7)
  
  val f3 = sqrt((x `^` 2) + (y `^` 2))
  // `^` 是乘方的符号形式，也可写作 pow
  // 注意中缀表达式具有最低运算优先级，低于 +、-，因此作为和式、积式成分必须加括号
  ```

  只要其中至少包含一个未知数成分或其他表达式，整个对象会被自动推断为表达式。

  表达式可以在初等函数的范围内复合：

  ```kotlin
  val f: Expression = ...
  val f1 = E `^` f // E === kotlin.math.E
  ```
  
  如果有必要定义没有任何未知数存在的表达式（常数表达式），可使用常数表达式：

  ```kotlin
  // Constant(Double) 将一个有理数转换为常数表达式
  val c1 = Constant(1.0 + 2 + 3)
  
  // 这样也是正确的，因为常数表达式也是表达式成分，c2 会被推断为表达式
  val c2 = Constant(1.0) + 2 + 3
  ```

- 微分

  实际上微分也是一种表达式运算，将一个表达式转化为其微分式的表达式：

  ```kotlin
  val x by variable
  val y by variable
  val f = (sqrt(x * x + y) + 1) `^` 2
  val df = d(f) 
  println(df) // 打印：2 x dx + 2 (x^2 + y)^-0.5 x dx + dy + (x^2 + y)^-0.5 dy
  
  ...
  ```

  `dx`、`dy` 是所谓微元的东西，作为一种可乘除相消的因子参与运算。

  微分运算通过和式、积式和复合函数求导的链式法则排出函数部分，保留变量的微元。

  若 `$u`、`$v` 为两个不同的变量，定义 `d d $u ≡ d$u / d$v ≡ d$v / d$u ≡ Constant(.0)`。

  因此，`d(f)/d(x)` 就是 `f(x,y)` 对 `x` 的偏导数：

  ```kotlin
  ...
  
  val dfx = df / d(x)
  println(dfx) // 打印：2 x + 2 (x^2 + y)^-0.5 x
  
  ...
  ```

  可以保存多重微分式，降低求高阶导数的开销：

  ```kotlin
  ...
  
  val ddf = d(df) // 这里实际上完成了全部的微分运算，所谓“求偏导”只是微分项的指数加减法
  val dx = d(x)
  val dy = d(y)
  
  println("∂2f / ∂x2  = ${ddf / (dx * dx)}")
  println("∂2f / ∂x∂y = ${ddf / (dx * dy)}")
  println("∂2f / ∂y2  = ${ddf / (dy * dy)}")
  ```

  ```bash
  ∂2f / ∂x2  = 2 (x^2 + y)^-0.5 - 2 (x^2 + y)^-1.5 x^2 + 2
  ∂2f / ∂x∂y = -2 (x^2 + y)^-1.5 x
  ∂2f / ∂y2  = -0.5 (x^2 + y)^-1.5
  ```

- 代入

  代入是化简、消元一类操作最常见的形式。

  ```kotlin
  val x by variable
  val y by variable
  
  val f = x `^` 2
  println(f.substitute(x, 2)) // 打印：4
  println(f.substitute { this[x] = x * y }) // 打印：x^2 y^2
  ```

  下列代换都受到支持：

  - 求值：把变量代换为常量
  - 复合展开：把变量代换为表达式
  - 换元：把表达式带换成变量

  > 但是暂时还无法实现对和式和积式的部分代换：例如从 `4 x + 4 y` 中代换掉 `x + y` 或从 `x y z` 中代换掉 `x y`。

- 求梯度

  求梯度需要了解另一类对象，矢量场 `Field`，其主要成员是一个变量到表达式的映射，`{x1 -> f1(x1), x2 -> f2(x2), ... , xn -> fn(xn)}`。
  
  这可以看作一个各维度具名且由表达式构成的 n 维向量函数，其输入输出在变量空间 `{x1, x2, ... ,xn}` 中。
  
  构造变量空间和标量场表达式后可计算梯度：
  
  ```kotlin
  val space by variableSpace(...)
  val f = ...
  val grad = space.gradientOf(f)
  ```
  
  矢量场可求模转化为标量场：
  
  ```kotlin
  class Field{
      ...
      
      val length = sqrt(expressions.sumBy { (_, e) -> e `^` 2 })
      
      ...
  }
  ```
  
## 求解无约束优化问题

  ### 形式

  无约束优化问题指的是：

  根据 `n` 条线索 `{f<i>(x) == 0 | i ∈ [1,n]}`，寻找最优的 `x` 的问题。

  > 其中 `x` 是 `ExpressionVector`，这是一种各维度具名的表达式向量。
  >
  > 具名是为了方便部分在多个不同变量空间上操作。

  ### 损失函数

  求解无约束优化问题，第一步是把线索转化为损失函数，把找到最优解的问题转化为求损失函数极小值的问题。

  最常用的损失函数是**均方差**：`e(x) = Σi (f<i>(x))^2 / 2n`。

  ### 随机化

  有 2 种方式使用线索的方式：

  - *批量* - 每次迭代都使用所有线索
  
  - *随机* - 每次迭代使用 1 条线索

  批量方法适用于变量空间较小的问题。

  这类问题每次迭代比较快，因此主要优化方向为**提高准确性**和**减小迭代次数**。一次使用全部线索可以找到全局最优的迭代方向，振荡可能性较小。

  随机方法适用于变量空间较大，或有许多相似线索（病态）的问题。

  若变量空间很大，一次求得全部梯度的成本较高，且即使是强凸的问题，极值点附近也难以得到好的步长，因此不太可能得到精度特别高的解。

  对于相似但不完全相同的两条线索，批量求解总是付出两倍的计算开销，但随机优化时，只要对使用线索的顺序稍作优化，就可以省去这部分开销。另外，变量空间很大的问题，病态的可能性也很高。

  令有一次使用一部分线索的*小批量*方法，属于这两种方法的综合形式。

  对于需要精度较高，变量空间又很大的问题，可以随着迭代逐步提高每次迭代使用的线索量，以同时获得这些方案的优势。

  ### 方向

  每一次迭代，首先要决定在变量空间上前进的方向。对于求损失函数极值的问题，方向由损失函数的一阶或二阶微分决定。

  - 一阶方法 - 梯度下降法
  - 二阶方法 - 牛顿法

  > 对于牛顿法，有以下 2 条注意事项：
  >
  > - 由于需要海森矩阵可逆，故要求损失函数与变量空间中全部维度相关，所以随机化迭代不太可能适用牛顿法
  > - 牛顿法找到的是去往最近导函数零点的方向，这有可能是极大值点、极小值点或鞍点，因此通常会将牛顿方向与梯度方向求点积，若点积不大于 0，说明此次牛顿法找到的不是梯度下降方向，大概率不通向极小值点，此时可以退化到一阶方法以保证稳定性。

  ### 步长

  决定方向后，需要再决定在方向上前进的步长。步长有 2 种决定方法：

  - 修饰 - 步长与方向同时从微分得到，通常是梯度的模长乘上一个固定系数
  - 再优化 - 确定方向后，求损失函数的方向导数，在方向导数上再搜索极小值

  ### 常见组合

|   方法名称   | 随机化 | 方向 |  步长  |
| :----------: | :----: | :--: | :----: |
|   梯度下降   |  批量  | 一阶 |  修饰  |
| 随机梯度下降 |  随机  | 一阶 |   -    |
|   最速下降   |  批量  | 一阶 | 再优化 |
|    牛顿法    |  批量  | 二阶 |  修饰  |
|  阻尼牛顿法  |  批量  | 二阶 | 再优化 |

  ### 示例

  ```kotlin
  // 变量空间
  val space: VariableSpace
  // 线索集
  val samples: List<Expression>
  // 均方损失函数
  val error = samples.map { it `^` 2 / (2 * samples.size) }
  // 梯度下降
  batchGD(error.sum(), space) { l -> 1.0 * l }
  // 最速下降
  fastestBatchGD(error.sum(), space)
  // 随机梯度下降
  stochasticGD(error) { batchGD(it, space) { l -> 1.0 * l } }
  // 随机化最速下降
  stochasticGD(error) { fastestBatchGD(it, space) }
  // 牛顿法
  newton(error.sum(), space)
  // 阻尼牛顿法
  dampingNewton(error.sum(), space)
  ```

  

import os.Path
import $file.benchmark, benchmark.BenchmarksModule
import $file.publishable, publishable.PublishableModule
import $file.facadeGenerator, facadeGenerator.FacadeGenerationModule
import mill._, scalalib._, scalafmt._
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:`
import mill.contrib.buildinfo.BuildInfo
import com.github.lolgab.mill.mima._

import $ivy.`de.tototec::de.tobiasroeser.mill.jacoco_mill0.10:0.0.2`
import de.tobiasroeser.mill.jacoco.JacocoTestModule

object v {
  val munit = "1.0.0-M6"
  val jmh = "1.33"
  val jnr = "2.2.3"
  val jna = "5.9.0"
  val scoverage = "1.4.0"
}

trait BaseModule extends ScalaModule with ScalafmtModule {
  def scalaVersion = "3.2.0"
  // def scoverageVersion = "2.0.5"

  val munitVersion = "1.0.0-M6"
  val jmhV = "1.33"
  val jnr = "2.2.3"
  val jna = "5.9.0"

  def ivyDeps = Agg(
    ivy"org.scala-lang::scala3-staging:${scalaVersion()}"
  )

  def scalacOptions = Seq(
    "-deprecation",
    "-Wunused:all",
    "-unchecked",
    "-Xcheck-macros",
    "-Xprint-suspension",
    "-Xsemanticdb",
    "-Yexplicit-nulls",
    "-Ysafe-init",
    "-source:future",
    "-Ykind-projector"
  )
}

// object `sffi-core` extends BaseModule with PublishableModule {
//   def pomSettings = pomTemplate("scala-ffi-core")
//  object test extends Tests with TestModule.Munit {
//     def moduleDeps = Seq(`sffi-core`)

//     def ivyDeps = Agg(ivy"org.scalameta::munit:${v.munit}")
//   }

// }

// object `sffi-j17` extends BaseModule with PublishableModule with BenchmarksModule {
//   def moduleDeps = Seq(`sffi-core`)
//   object bench extends Benchmarks {
//     def jmhVersion = "1.33"
//   }

//   object test extends Tests with TestModule.Munit {
//     def moduleDeps = Seq(`sffi-core`.test, `sffi-j17`)
//     def forkArgs = Seq(
//       "--add-modules",
//       "jdk.incubator.foreign",
//       "--enable-native-access",
//       "ALL-UNNAMED"
//     )

//     def ivyDeps = Agg(ivy"org.scalameta::munit:${v.munit}")
//   }

//   def pomSettings = pomTemplate("scala-ffi-java-17")
// }

object core extends BaseModule with PublishableModule with FacadeGenerationModule {

  def pomSettings = pomTemplate("slinc-core")

  def specializationArity = 4

  object test extends Tests with TestModule.Munit with JacocoTestModule {
    def ivyDeps = Agg(ivy"org.scalameta::munit:$munitVersion")
  }

}

object j17 extends BaseModule with PublishableModule with BenchmarksModule {
  def moduleDeps = Seq(core)
  def pomSettings = pomTemplate("slinc-java-17")

  def javacOptions = super.javacOptions() ++ Seq("--add-modules=jdk.incubator.foreign")

  object test extends Tests with TestModule.Munit with JacocoTestModule {
    def ivyDeps = Agg(ivy"org.scalameta::munit:$munitVersion")
    def forkArgs = super.forkArgs() ++ Seq(
      "--add-modules=jdk.incubator.foreign",
      "--enable-native-access=ALL-UNNAMED"
    )
  }

  //todo: remove this nasty hack needed for jacoco coverage reports
  object benchmarks extends BaseModule{
    def moduleDeps = Seq(j17)
    override def scalaVersion = j17.scalaVersion
    override def scalacOptions = j17.scalacOptions
    object test extends Benchmarks {
      def jmhVersion = jmhV
      def forkArgs = super.forkArgs() ++ Seq(
        "--add-modules=jdk.incubator.foreign",
        "--enable-native-access=ALL-UNNAMED"
      )
    }
  }

}
// object slinc
//     extends ScalaModule
//     with PlatformTypegen
//     with PublishableModule
//     with BenchmarksModule
//     with VariadicGen {

//   val ffiV = "0.1.1-1-aad70b-DIRTY4d5f65ba"
//   def moduleDeps = Seq(polymorphics)
//   def ivyDeps = Agg(
//     ivy"org.typelevel::cats-core:2.7.0",
//     ivy"io.gitlab.mhammons::ffi-j17:$ffiV",
//     ivy"io.gitlab.mhammons::ffi-j18:$ffiV"
//   )
//   def scalaVersion = "3.1.1"
//   def pomSettings = pomTemplate("SLinC - Scala <-> C Interop")

//   def scalacOptions = Seq(
//     "-deprecation",
//     "-Wunused:all",
//     "-unchecked",
//     "-Xcheck-macros",
//     "-Xprint-suspension"
//   )

//   def scalaDocOptions = T {
//     super.scalaDocOptions() ++ Seq(
//       "-project-logo",
//       (millSourcePath / "docs" / "images" / "logo.svg").toString,
//       "-social-links:github::https://github.com/markehammons/SLInC,custom::https://gitlab.com/mhammons/slinc::gitlab-white.svg::gitlab-black.svg"
//     )
//   }

//   def forkArgs = Seq(
//     "--add-modules",
//     "jdk.incubator.foreign",
//     "--enable-native-access",
//     "ALL-UNNAMED"
//   )

//   def docSources = T.sources {
//     super.docSources() :+ PathRef(millSourcePath / "docs")
//   }
//   object test extends Tests with TestModule.Munit with BuildInfo {
//     def scalacOptions = Seq(
//       "-deprecation",
//       "-Wunused:all",
//       "-unchecked",
//       "-Xcheck-macros",
//       "-Xprint-suspension",
//       "-Xsemanticdb",
//       "-Ydump-sbt-inc"
//     )

//     def forkArgs = Seq(
//       "--add-modules",
//       "jdk.incubator.foreign",
//       "--enable-native-access",
//       "ALL-UNNAMED"
//     )

//     def ivyDeps = Agg(ivy"org.scalameta::munit::${v.munit}")
//     def nativeSource = T.sources { millSourcePath / "native" }
//     def buildInfoMembers = T {
//       compileNative()
//         .map(p => p.path.last.stripSuffix(".so") -> p.path.toString())
//         .toMap
//     }

//     def buildInfoPackageName = Some("io.gitlab.mhammons.slinc")
//     def compileNative = T {
//       val nativeFiles = nativeSource().head.path
//       val cFiles = os.list(nativeFiles).filter(_.last.endsWith(".c"))
//       cFiles
//         .flatMap { p =>
//           val soLocation =
//             T.dest / s"lib${p.last.stripSuffix(".c")}.so"
//           os.proc(
//             "gcc",
//             "-shared",
//             "-fPIC",
//             "-Wall",
//             "-o",
//             T.dest / s"lib${p.last.stripSuffix(".c")}.so",
//             p
//           ).call()
//           List(PathRef(soLocation))
//         }

//     }

//     override def compile = T {
//       compileNative()
//       super.compile()
//     }
//   }

//   object bench extends Benchmarks {
//     def jmhVersion = "1.33"
//   }
// }

// object cstd extends ScalaModule with benchmark.BenchmarksModule {
//   def scalaVersion = "3.1.1"
//   def moduleDeps = Seq(slinc)

//   object test extends Tests with TestModule.Munit {
//     def scalacOptions = Seq(
//       "-deprecation",
//       "-Wunused:all",
//       "-unchecked",
//       "-Xcheck-macros",
//       "-Xprint-suspension",
//       "-Xsemanticdb"
//     )

//     def forkArgs = Seq(
//       "--add-modules",
//       "jdk.incubator.foreign",
//       "--enable-native-access",
//       "ALL-UNNAMED"
//     )

//     def ivyDeps = Agg(ivy"org.scalameta::munit::${v.munit}")
//   }

//   object bench extends Benchmarks {
//     def jmhVersion = "1.33"

//     def forkArgs = Seq(
//       "--add-modules",
//       "jdk.incubator.foreign",
//       "--enable-native-access",
//       "ALL-UNNAMED"
//     )

//   }
// }

// object openblas extends ScalaModule with benchmark.BenchmarksModule {
//   def scalaVersion = "3.1.1"
//   def moduleDeps = Seq(slinc)

//   object bench extends Benchmarks {
//     def jmhVersion = "1.33"

//     def forkArgs = Seq(
//       "--add-modules=jdk.incubator.foreign",
//       "--enable-native-access=ALL-UNNAMED"
//     )

//     override def ivyDeps = super.ivyDeps() ++ Agg(
//       ivy"org.jblas:jblas:1.2.5"
//     )
//   }
// }

// object polymorphics extends ScalaModule with publishable.PublishableModule {
//   def publishVersion = "0.1.1"
//   def mimaPreviousVersions = Seq("0.1.1")
//   def scalaVersion = "2.13.7"
//   def pomSettings = pomTemplate(
//     "Shim to use polymorphic methods from scala 3 <DON'T DEPEND ON ME>"
//   )
//   def scalacPluginIvyDeps = Agg(ivy"org.scalameta:::semanticdb-scalac:4.4.32")
//   def scalacOptions = Seq("-Yrangepos")
// }

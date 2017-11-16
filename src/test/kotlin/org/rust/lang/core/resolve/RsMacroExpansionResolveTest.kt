/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve


class RsMacroExpansionResolveTest: RsResolveTestBase() {
    fun `test lazy static`() = checkByCode("""
        #[macro_use]
        extern crate lazy_static;

        struct Foo {}
        impl Foo {
            fn new() -> Foo { Foo {} }
            fn bar(&self) {}
        }     //X

        lazy_static! { static ref FOO: Foo = Foo::new(); }

        fn main() {
            FOO.bar()
        }      //^
    """)

    val d = '$'
    fun `test expand items star`() = checkByCode("""
        macro_rules! if_std {
            ($d(${d}i:item)*) => ($d(
                #[cfg(feature = "use_std")]
                ${d}i
            )*)
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        if_std! {
            fn foo() -> Foo { Foo }
        }

        fn main() {
            foo().bar()
        }       //^

    """)
}

/**
 * Example from
 * "The Omega test: a fast and practical integer 
 * programming algorithm for dependence analysis", Pugh
 */

\functions {
  int x, y;
}

\problem {
\part[left](3 <= 11*x+13*y & 11*x+13*y <= 21)
&
\part[right](-8 <= 7*x -9*y & 7*x -9*y <= 6)
->
 false
}

\interpolant{left; right}

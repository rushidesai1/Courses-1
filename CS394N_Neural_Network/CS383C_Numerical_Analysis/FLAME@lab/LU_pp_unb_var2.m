%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%% Homework 06: LU with Partial Pivoting (Overwrite A)
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Copyright 2014 The University of Texas at Austin
%
% For licensing information see
%                http://www.cs.utexas.edu/users/flame/license.html 
%                                                                                 
% Programmed by: Jimmy Lin
%                jimmylin@utexas.edu
function [ A_out, p_out ] = LU_partial_pivot_unb( A, p )
  [ ATL, ATR, ...
    ABL, ABR ] = FLA_Part_2x2( A, ...
                               0, 0, 'FLA_TL' );
  [ pT, ...
    pB ] = FLA_Part_2x1( p, ...
                         0, 'FLA_TOP' );
  while ( size( ATL, 1 ) < size( A, 1 ) )
    [ A00,  a01,     A02,  ...
      a10t, alpha11, a12t, ...
      A20,  a21,     A22 ] = FLA_Repart_2x2_to_3x3( ATL, ATR, ...
                                                    ABL, ABR, ...
                                                    1, 1, 'FLA_BR' );
    [ p0, ...
      pi1, ...
      p2 ] = FLA_Repart_2x1_to_3x1( pT, ...
                                    pB, ...
                                    1, 'FLA_BOTTOM' );
    %------------------------------------------------------------%
    [ ~ , pi1 ] = max ([alpha11; a21]);
    if pi1 ~= 1,
        P = eye(size(A22)+[1 1]);
        P(1, 1) = 0; P(1, pi1) = 1;
        P(pi1, pi1) = 0; P(pi1, 1) = 1;
        new = P * [a10t, alpha11, a12t; A20, a21, A22];
        sep_col = size(ATL, 2);
        a10t = new(1, 1:sep_col);
        alpha11 = new(1, sep_col+1);
        a12t = new(1, (sep_col+2):end);
        A20 = new(2:end, 1:sep_col);
        a21 = new(2:end, sep_col+1);
        A22 = new(2:end, (sep_col+2):end);
    end
    a21 = a21 / alpha11;
    A22 = A22 - a21 * a12t;
    %------------------------------------------------------------%
    [ ATL, ATR, ...
      ABL, ABR ] = FLA_Cont_with_3x3_to_2x2( A00,  a01,     A02,  ...
                                             a10t, alpha11, a12t, ...
                                             A20,  a21,     A22, ...
                                             'FLA_TL' );
    [ pT, ...
      pB ] = FLA_Cont_with_3x1_to_2x1( p0, ...
                                       pi1, ...
                                       p2, ...
                                       'FLA_TOP' );
  end
  A_out = [ ATL, ATR
            ABL, ABR ];
  p_out = [ pT
            pB ];
return